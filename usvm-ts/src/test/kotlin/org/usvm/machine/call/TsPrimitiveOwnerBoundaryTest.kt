package org.usvm.machine.call

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
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsPrimitiveOwnerBoundaryTest {
    private val source = loadEtsFileAutoConvert(
        getResourcePath("/models/ForeignPrimitiveHelpers.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(source))

    @Test
    fun `foreign string helper cannot fabricate a code unit with all models enabled`() {
        assertForeignHelperResidual("foreignString")
    }

    @Test
    fun `foreign array helper cannot mutate storage with all models enabled`() {
        assertForeignHelperResidual("foreignArray")
    }

    @Test
    fun `foreign declarations cannot use string predicate or range helpers`() {
        assertForeignHelperResidual("foreignWhitespace")
        assertForeignHelperResidual("foreignStringRange")
    }

    private fun assertForeignHelperResidual(methodName: String) {
        val method = scene.projectClasses.single { it.name == "ForeignPrimitiveHelpers" }
            .methods
            .single { it.name == methodName }
        val observer = RecordingObserver()

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }

        assertTrue(states.isEmpty())
        assertTrue(observer.events.isNotEmpty())
        assertTrue(observer.events.none { event -> event.decision is TsUnknownCallDecision.ModelApplied })
        assertTrue(observer.events.any { event -> event.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }

    private class RecordingObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
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
