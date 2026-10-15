package org.usvm.machine

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsSharedAnalysisTest {
    private val file = loadEtsFileAutoConvert(
        getResourcePath("/models/SharedAnalysis.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(projectFiles = listOf(file))
    private val options = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
        solverType = SolverType.YICES,
    )
    private val tsOptions = TsOptions(
        unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
        maxArraySize = 2,
    )

    @Test
    fun `step limit and zero time budget have distinct metadata`() {
        val limited = analyze(options = options.copy(stepLimit = 1uL))
        val timedOut = analyze(options = options.copy(timeout = Duration.ZERO))

        assertEquals(TsAnalysisStopReason.STOPPED, limited.stopReason)
        assertFalse(limited.timedOut)
        assertEquals(TsAnalysisStopReason.STOPPED, timedOut.stopReason)
        assertTrue(timedOut.timedOut)
        assertTrue(timedOut.states.isEmpty())
    }

    @Test
    fun `stopped residual and runtime limitation are distinct and reset per analysis`() {
        val events = mutableListOf<String>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) { events += "call" }
        }

        TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer).use { machine ->
            val unknown = machine.analyzeWithMetadata(methods = listOf(method("unknown")))
            val limited = machine.analyzeWithMetadata(methods = listOf(method("limited")))
            val success = machine.analyzeWithMetadata(methods = listOf(method("success")))

            assertTrue(unknown.unsupportedCall)
            assertFalse(unknown.runtimeLimited)
            assertFalse(unknown.engineFailed)
            assertFalse(limited.unsupportedCall)
            assertTrue(limited.runtimeLimited)
            assertFalse(limited.engineFailed)
            assertFalse(success.unsupportedCall)
            assertFalse(success.runtimeLimited)
            assertFalse(success.engineFailed)
            assertTrue(limited.unsupportedPaths.any { "length bound" in it }, limited.unsupportedPaths.toString())
            assertEquals(listOf("call"), events)
        }
    }

    @Test
    fun `fresh residual return is not reported as a stopped call`() {
        val result = TsMachine(
            scene = scene,
            options = options,
            tsOptions = tsOptions.copy(unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
        ).use { it.analyzeWithMetadata(methods = listOf(method("unknown"))) }

        assertFalse(result.unsupportedCall)
        assertFalse(result.engineFailed)
        assertTrue(result.states.isNotEmpty())
    }

    @Test
    fun `suppressed step failure is reported and reset for a later analysis`() {
        var shouldFail = true
        val observer = object : TsInterpreterObserver {
            override fun onReturnStatement(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsReturnStmt,
                scope: TsStepScope,
            ) {
                check(!shouldFail) { "Injected interpreter callback failure" }
            }
        }

        TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer).use { machine ->
            val failed = machine.analyzeWithMetadata(methods = listOf(method("success")))
            shouldFail = false
            val recovered = machine.analyzeWithMetadata(methods = listOf(method("success")))

            assertTrue(failed.engineFailed)
            assertTrue(failed.states.isEmpty())
            assertFalse(recovered.engineFailed)
            assertEquals(1, recovered.states.size)
        }
    }

    @Test
    fun `observers see standalone calls once and assignments after their value is stored`() {
        val calls = mutableListOf<String>()
        var assignments = 0
        val observer = object : TsInterpreterObserver {
            override fun onCallStatement(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsCallStmt,
                scope: TsStepScope,
            ) {
                calls += stmt.expr.callee.name
            }

            override fun onAssignmentCompleted(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsAssignStmt,
                scope: TsStepScope,
            ) {
                if ((stmt.lhv as? EtsLocal)?.name != "result") return

                assignments++
                val value = stmt.lhv.accept(simpleValueResolver)
                val expected = scope.calcOnState { ctx.mkFp(7.0, ctx.fp64Sort) }

                assertEquals(expected, value)
            }
        }

        TsMachine(
            scene = scene,
            options = options.copy(throwExceptionOnStepFailure = true),
            tsOptions = tsOptions,
            observer = observer,
        ).use { machine ->
            machine.analyze(methods = listOf(method("observed")))
            machine.analyze(methods = listOf(method("unknown")))
        }

        assertEquals(listOf("callee"), calls)
        assertEquals(1, assignments)
    }

    private fun method(name: String) = scene.projectClasses.single { it.name == "SharedAnalysis" }
        .methods
        .single { it.name == name }

    private fun analyze(options: UMachineOptions) = TsMachine(
        scene = scene,
        options = options,
        tsOptions = tsOptions,
    ).use { it.analyzeWithMetadata(methods = listOf(method("success"))) }
}
