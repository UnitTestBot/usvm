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
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.call.TsUnknownCallOutcome
import org.usvm.machine.state.TsState
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class UnknownStringConcatTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/UnknownStringConcat.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `fresh string return can be concatenated`() {
        val observer = RecordingObserver()

        val states = analyze(methodName = "concatOpaqueString", observer = observer)

        assertTrue(states.isNotEmpty())
        assertTrue(observer.events.any { it.outcome == TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN })
        assertTrue(observer.limitations.isEmpty())
    }

    @Test
    fun `symbolic number to string is an explicit limitation`() {
        val observer = RecordingObserver()

        val states = analyze(methodName = "concatOpaqueNumber", observer = observer)

        assertTrue(states.isEmpty())
        assertEquals(
            TsRuntimeFeatureLimitationReason.STRING_CONCAT_OPERAND_CONVERSION,
            observer.limitations.single().reason,
        )
    }

    private fun analyze(methodName: String, observer: RecordingObserver): List<TsState> {
        val clazz = scene.projectClasses.single { it.name == "UnknownStringConcat" }
        val method = clazz.methods.single { it.name == methodName }

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(
                unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
                unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            ),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }
    }

    private class RecordingObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }

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
