package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsParametersState
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.util.TsTestResolver
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CallsConcreteHeapTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `concrete graph roundtrip preserves aliases cycles prototypes and own undefined`() {
        val fields = linkedMapOf<String, TsTestValue>()
        val receiver = TsTestValue.TsClass(name = "Object", properties = fields)
        val shared = TsTestValue.TsArray(listOf(TsTestValue.TsString("\uD800")))
        fields["self"] = receiver
        fields["items"] = shared
        fields["present"] = TsTestValue.TsUndefined
        fields["negativeZero"] = TsTestValue.TsNumber.TsDouble(-0.0)
        fields["nan"] = TsTestValue.TsNumber.TsDouble(Double.NaN)
        fields["positiveInfinity"] = TsTestValue.TsNumber.TsDouble(Double.POSITIVE_INFINITY)
        fields["negativeInfinity"] = TsTestValue.TsNumber.TsDouble(Double.NEGATIVE_INFINITY)
        fields["nullable"] = TsTestValue.TsNull
        fields["logical"] = TsTestValue.TsBoolean(value = true)
        val signature = EtsClassSignature(
            name = "Object",
            file = EtsFileSignature(projectName = "fixture", fileName = "Target.ts"),
        )
        val prototypes = IdentityHashMap<TsTestValue.TsClass, CallsConcretePrototype>()
        prototypes[receiver] = CallsConcretePrototype(signature = signature, ordinaryObject = false)
        val state = TsParametersState(
            thisInstance = receiver,
            parameters = listOf(shared, receiver),
            globals = emptyMap(),
        )

        val encoded = encodeCallsArtifact(concreteCallsInputHeap(inputs = state, prototypes = prototypes))
        val restored = CallsExperimentJson.json.decodeFromString<CallsConcreteHeap>(encoded)
        Files.writeString(directory.resolve("environment.json"), encoded)

        assertEquals(2, restored.objects.size)
        assertEquals(restored.receiver, restored.arguments[1])
        assertEquals(signature.toString(), restored.objects.first().classSignature)
        replayGraph()
    }

    @Test
    fun `project class Object retains its source qualified prototype`() {
        val source = directory.resolve("Target.ts")
        Files.writeString(
            source,
            "export class Object { value: number = 0; } " +
                "export class Holder { read(value: Object): number { return value.value; } }",
        )
        val file = loadEtsFileAutoConvert(path = source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(projectFiles = listOf(file))
        val owner = scene.projectClasses.single { it.name == "Object" }
        val method = scene.projectClasses.single { it.name == "Holder" }.methods.single { it.name == "read" }
        val state = TsMachine(
            scene = scene,
            options = UMachineOptions(solverType = SolverType.YICES),
            tsOptions = TsOptions(),
        ).use { machine -> machine.analyze(methods = listOf(method)).single() }
        val prototypes = IdentityHashMap<TsTestValue.TsClass, CallsConcretePrototype>()
        val resolver = TsTestResolver(onClassResolved = { value, clazz ->
            prototypes[value] = CallsConcretePrototype(signature = clazz.signature, ordinaryObject = false)
        })

        val inputs = resolver.resolveInputs(method = method, state = state)
        val input = assertIs<TsTestValue.TsClass>(inputs.parameters.single())
        val graph = concreteCallsInputHeap(inputs = inputs, prototypes = prototypes)

        assertEquals(owner.signature, prototypes[input]?.signature)
        assertEquals(owner.signature.toString(), graph.objects.single { it.className == "Object" }.classSignature)
    }

    private fun replayGraph() {
        val source = """
            import { readFileSync } from 'node:fs';
            class ObjectTarget {}
            ${callsConcretePrimitiveDecoderSource()}
            ${callsConcreteHeapRestoreSource()}
            export function checkGraph(_sentinel: boolean): boolean {
              const graph = JSON.parse(readFileSync(new URL('./environment.json', import.meta.url), 'utf8'));
              const prototypes = new Map([[graph.objects[0].classSignature, ObjectTarget.prototype]]);
              const restored = restoreCallsConcreteHeap(graph, prototypes, decodeCallsConcretePrimitive);
              const receiver = restored.receiver;
              return Object.getPrototypeOf(receiver) === ObjectTarget.prototype &&
                     receiver.self === receiver && receiver.items === restored.arguments[0] &&
                     receiver === restored.arguments[1] && Object.hasOwn(receiver, 'present') &&
                     receiver.present === undefined && !Object.hasOwn(receiver, 'missing') &&
                     receiver.items[0].charCodeAt(0) === 0xD800 &&
                     Object.is(receiver.negativeZero, -0) && Number.isNaN(receiver.nan) &&
                     receiver.positiveInfinity === Infinity && receiver.negativeInfinity === -Infinity &&
                     receiver.nullable === null && receiver.logical === true;
            }
        """.trimIndent()
        Files.writeString(directory.resolve("replay.mts"), source)
        val property = PropertyDefinition(
            id = PropertyId("calls.concrete-heap-roundtrip"),
            inputs = listOf(PropertyInput(name = "sentinel", domain = BooleanDomain)),
            predicate = TypeScriptEntryPoint(module = "replay.mts", exportName = "checkGraph"),
        )

        val result = FastCheckBackend(sourceRoots = listOf(directory)).run(
            property = property,
            configuration = PropertyRunConfiguration(seed = 17, numRuns = 1, timeoutMillis = 20_000),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status, result.toString())
    }
}
