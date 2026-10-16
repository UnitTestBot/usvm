package org.usvm.util

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.TsParametersState
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.state.TsState
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsInputWitnessResolverTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/lang/ConcreteWitnessInput.ts"
    override val scene: EtsScene = loadScene(sourcePath)

    @Test
    fun `nested stopped call preserves original receiver object and array inputs before mutation`() {
        val method = getMethod(methodName = "mutateAndStop", className = "ConcreteWitnessInput")
        var receiver: UExpr<*>? = null
        var inputs = emptyList<UExpr<*>>()
        val witnesses = mutableListOf<TsParametersState>()
        val failures = mutableListOf<String>()
        val observer = object : TsInterpreterObserver {
            override fun onPathStopped(state: TsState, reason: String) {
                runCatching {
                    check(state.callStack.size > 1)
                    TsTestResolver().resolveInputs(
                        method = method,
                        state = state,
                        inputValues = inputs,
                        receiverValue = receiver,
                    )
                }.onSuccess { witnesses += it }.onFailure { failures += it.stackTraceToString() }
            }
        }

        val analysis = TsMachine(
            scene = scene,
            options = analysisOptions,
            tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet())),
            observer = observer,
        ).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method), configureInitialState = { _, state ->
                receiver = state.memory.read(mkRegisterStackLValue(state.ctx.addressSort, idx = 0))
                inputs = method.parameters.mapIndexed { index, parameter ->
                    val sort = state.ctx.typeToSort(parameter.type)
                    state.memory.read(mkRegisterStackLValue(sort, idx = index + 1))
                }
            })
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        assertTrue(witnesses.isNotEmpty(), analysis.toString())
        val witness = witnesses.single()
        val beforeReceiver = assertIs<TsTestValue.TsClass>(witness.thisInstance)
        val beforeBox = assertIs<TsTestValue.TsClass>(witness.parameters[0])
        val beforeArray = assertIs<TsTestValue.TsArray<*>>(witness.parameters[1])
        val receiverValue = assertIs<TsTestValue.TsNumber>(beforeReceiver.properties["value"]).number
        val boxValue = assertIs<TsTestValue.TsNumber>(beforeBox.properties["value"]).number
        val arrayValue = assertIs<TsTestValue.TsNumber>(beforeArray.values.single()).number
        assertEquals(11.0, receiverValue)
        assertEquals(12.0, boxValue)
        assertEquals(13.0, arrayValue)

        val script = getResourcePath(sourcePath).readText() + """
            const receiver = Object.create(ConcreteWitnessInput.prototype);
            receiver.value = $receiverValue;
            const box = Object.create(ConcreteWitnessBox.prototype);
            box.value = $boxValue;
            const items = [$arrayValue];
            try { receiver.mutateAndStop(box, items); } catch (error) {
                if (!(error instanceof ReferenceError)) throw error;
            }
            if (receiver.value !== 91 || box.value !== 92 || items[0] !== 93) {
                throw Error("The resolved inputs did not reproduce the mutated stopped prefix");
            }
        """.trimIndent()
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "stopped-input-prefix",
            timeoutMessage = "Stopped input-prefix replay timed out",
        )
    }

    @Test
    fun `array identity survives input resolution`() {
        val method = getMethod(methodName = "alias", className = "ConcreteWitnessInput")
        val states = TsMachine(scene = scene, options = analysisOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyze(methods = listOf(method))
        }

        val witnesses = states.map { TsTestResolver().resolveInputs(method = method, state = it) }

        assertTrue(witnesses.any { it.parameters[0] === it.parameters[1] })
    }

    private val analysisOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        stopOnCoverage = 0,
    )
}
