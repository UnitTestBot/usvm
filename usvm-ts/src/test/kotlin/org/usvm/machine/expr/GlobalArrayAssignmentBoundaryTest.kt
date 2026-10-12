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
import kotlin.test.assertTrue
import kotlin.time.Duration

class GlobalArrayAssignmentBoundaryTest {
    @Test
    fun `fractional global array assignment stops as a named property limitation`() {
        val sourceFile = loadEtsFileAutoConvert(
            getResourcePath("/models/GlobalArrayAssignmentBoundary.ts"),
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = EtsScene(listOf(sourceFile))
        val method = scene.projectClasses
            .single { it.name == "GlobalArrayAssignmentBoundary" }
            .methods
            .single { it.name == "readSecondValue" }
        val observer = RecordingObserver()

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(maxArraySize = 16),
            observer = observer,
        ).use { machine ->
            machine.analyze(listOf(method))
        }

        assertTrue(states.isEmpty())
        assertEquals(
            TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            observer.limitations.single().reason,
        )
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
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
