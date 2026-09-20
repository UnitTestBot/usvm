package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.util.TsMethodTestRunner
import kotlin.test.assertEquals
import kotlin.time.Duration

class TsMachineCompletionTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticOverloads.ts")

    @Test
    fun `analysis distinguishes path exhaustion from strategy stop`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val exhaustedOptions = UMachineOptions(
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
