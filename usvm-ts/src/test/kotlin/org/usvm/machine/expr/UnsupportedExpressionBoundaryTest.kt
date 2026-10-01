package org.usvm.machine.expr

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class UnsupportedExpressionBoundaryTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/UnsupportedExpressionBoundary.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `regular expression literal stops with an observed limitation`() {
        assertLimitation(
            methodName = "regularExpressionLiteral",
            reason = TsRuntimeFeatureLimitationReason.REGULAR_EXPRESSION_LITERAL,
        )
    }

    @Test
    fun `used catch value stops with an observed limitation`() {
        assertLimitation(
            methodName = "caughtExceptionValue",
            reason = TsRuntimeFeatureLimitationReason.CAUGHT_EXCEPTION_VALUE,
        )
    }

    private fun assertLimitation(methodName: String, reason: TsRuntimeFeatureLimitationReason) {
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == methodName }
        val observer = RecordingObserver()

        TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }

        assertEquals(listOf(reason), observer.limitations.map { it.reason })
    }

    private class RecordingObserver : TsInterpreterObserver {
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            limitations += event
        }
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 20_000L,
            solverType = SolverType.Z3,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
