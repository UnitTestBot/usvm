package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.util.TsMethodTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsMachineCompletionTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticOverloads.ts")

    @Test
    fun `TypeScript defaults to Yices without replacing an explicitly selected solver`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")

        TsMachine(scene = scene, tsOptions = TsOptions()).use { machine ->
            assertEquals(SolverType.YICES, machine.options.solverType)
        }

        for (solverType in listOf(SolverType.YICES, SolverType.Z3)) {
            val options = UMachineOptions(
                solverType = solverType,
                stopOnCoverage = 0,
                timeout = Duration.INFINITE,
            )

            TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
                val outcome = machine.analyzeWithOutcome(methods = listOf(method))

                assertSame(options, machine.options)
                assertEquals(solverType, machine.options.solverType)
                assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
                assertTrue(outcome.states.isNotEmpty())
            }
        }
    }

    @Test
    fun `analysis distinguishes path exhaustion from strategy stop`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val exhaustedOptions = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )
        val stoppedOptions = exhaustedOptions.copy(stepLimit = 1uL)

        val exhausted = TsMachine(
            scene = scene,
            options = exhaustedOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }
        val stopped = TsMachine(
            scene = scene,
            options = stoppedOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, exhausted.stopReason)
        assertEquals(TsAnalysisStopReason.STOPPED, stopped.stopReason)
    }
}
