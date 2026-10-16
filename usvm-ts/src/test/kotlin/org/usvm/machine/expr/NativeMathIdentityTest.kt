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
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeMathIdentityTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene = loadScene("/models/NativeMathIdentity.ts")

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
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
    fun `module initializer reads exact native Math PI`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "initializedConstant"),
            { result -> result.number == Math.PI / 180 },
            invariants = arrayOf({ result -> result.number == Math.PI / 180 }),
        )
    }

    @Test
    fun `closure sees the native Math global`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "closureConstant"),
            { result -> result.number == Math.PI },
            invariants = arrayOf({ result -> result.number == Math.PI }),
        )
    }

    @Test
    fun `native Math aliases share one ordinary object and exact constants`() {
        for (name in listOf("aliasIdentity", "constants")) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(name = name),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `source local and parameter bindings named Math retain their own fields`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "localShadow"),
            { result -> result.number == 17.0 },
            invariants = arrayOf({ result -> result.number == 17.0 }),
        )
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "parameterShadow"),
            { result -> result.number == 19.0 },
            invariants = arrayOf({ result -> result.number == 19.0 }),
        )
    }

    @Test
    fun `native constant descriptor mutations remain explicit unsupported operations`() {
        for (name in listOf("writeConstant", "deleteConstant")) {
            val outcome = TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method(name = name)))
            }

            assertTrue(outcome.states.isEmpty(), name)
            assertEquals(1, outcome.unsupportedPaths.size, name)
            assertTrue(outcome.unsupportedPaths.single().contains("readonly native Math constants"), name)
        }
    }

    @Test
    fun `native JavaScript confirms initialized constants alias identity and shadowing`() {
        val source = getResourcePath("/models/NativeMathIdentity.ts").readText()
        val checks = """
            if (NativeMathIdentity.initializedConstant() !== Math.PI / 180) throw Error("module constant");
            if (NativeMathIdentity.closureConstant() !== Math.PI) throw Error("closure constant");
            if (NativeMathIdentity.aliasIdentity() !== 1) throw Error("alias identity");
            if (NativeMathIdentity.constants() !== 1) throw Error("native constants");
            if (NativeMathIdentity.localShadow() !== 17) throw Error("local shadow");
            if (NativeMathIdentity.parameterShadow() !== 19) throw Error("parameter shadow");
        """.trimIndent()

        assertNodeReplay(
            source = source + checks,
            directory = directory,
            name = "native-math-identity",
            timeoutMessage = "Native Math identity replay timed out",
        )
    }

    private fun method(name: String) = getMethod(methodName = name, className = "NativeMathIdentity")
}
