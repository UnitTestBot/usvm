package org.usvm.machine.expr

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.call.TsUnknownCallOutcome
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

class NumberWithoutSdkTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/NumberWithoutSdk.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `missing Number SDK class leaves constructor call residual`() {
        val clazz = scene.projectClasses.single { it.name == "NumberWithoutSdk" }
        val method = clazz.methods.single { it.name == "construct" }
        val observer = RecordingObserver()

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(
                unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
                unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            ),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }

        val freshReturnObserved = observer.events.any { event ->
            event.callee.name == CONSTRUCTOR_NAME &&
                event.outcome == TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
        }

        assertTrue(states.isNotEmpty())
        assertTrue(freshReturnObserved)
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
            solverType = SolverType.Z3,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
