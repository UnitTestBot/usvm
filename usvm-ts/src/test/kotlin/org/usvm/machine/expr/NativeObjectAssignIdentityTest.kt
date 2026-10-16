package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test

class NativeObjectAssignIdentityTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene = loadScene("/models/NativeObjectAssignIdentity.ts")
    override val tsOptions = TsOptions(
        unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
        unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
    )
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyze(methods = listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `extracted native assign has stable function identity`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "stableIdentity"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `local and captured unbound calls retain the native call boundary`() {
        val methods = listOf("extractedCall" to 2.0, "capturedCall" to 3.0, "nullishTarget" to 4.0, "boxedCall" to 8.0)
        for ((name, expected) in methods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(name = name),
                { result -> result.number == expected },
                invariants = arrayOf({ result -> result.number == expected }),
            )
        }
    }

    @Test
    fun `source object named Object executes its own assign method`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "localShadow"),
            { result -> result.number == 5.0 },
            invariants = arrayOf({ result -> result.number == 5.0 }),
        )
    }

    @Test
    fun `native assign replacement and deletion take precedence over its initial identity`() {
        val methods = listOf(
            "writeNative" to 5.0,
            "deleteNative" to 6.0,
            "originalAfterReplacement" to 7.0,
            "originalAfterDeletion" to 9.0,
        )
        for ((name, expected) in methods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(name = name),
                { result -> result.number == expected },
                invariants = arrayOf({ result -> result.number == expected }),
            )
        }
    }

    @Test
    fun `native JavaScript confirms extracted identity calls nullish target and source shadow`() {
        val source = getResourcePath("/models/NativeObjectAssignIdentity.ts").readText()
        val checks = """
            if (NativeObjectAssignIdentity.stableIdentity() !== 1) throw Error("identity");
            if (NativeObjectAssignIdentity.extractedCall() !== 2) throw Error("local call");
            if (NativeObjectAssignIdentity.capturedCall() !== 3) throw Error("captured call");
            if (NativeObjectAssignIdentity.boxedCall() !== 8) throw Error("boxed call");
            if (NativeObjectAssignIdentity.nullishTarget() !== 4) throw Error("nullish target");
            if (NativeObjectAssignIdentity.localShadow() !== 5) throw Error("source shadow");
            if (NativeObjectAssignIdentity.writeNative() !== 5) throw Error("replacement");
            if (NativeObjectAssignIdentity.originalAfterReplacement() !== 7) throw Error("captured replacement");
            if (NativeObjectAssignIdentity.deleteNative() !== 6) throw Error("deletion");
            if (NativeObjectAssignIdentity.originalAfterDeletion() !== 9) throw Error("captured deletion");
            Object.assign = extractedNativeAssign;
        """.trimIndent()

        assertNodeReplay(
            source = source + checks,
            directory = directory,
            name = "native-object-assign-identity",
            timeoutMessage = "Native Object.assign identity replay timed out",
        )
    }

    private fun method(name: String) = getMethod(methodName = name, className = "NativeObjectAssignIdentity")
}
