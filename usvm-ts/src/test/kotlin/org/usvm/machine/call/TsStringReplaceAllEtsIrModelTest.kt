package org.usvm.machine.call

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsStringReplaceAllEtsIrModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/StringReplaceAllEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `literal string replacement implements substitution and UTF16 boundaries`() {
        val cases = mapOf(
            "literalMatches" to "XX/aba",
            "emptySearch" to "-A-\uD83D-\uDE00-",
            "substitutions" to "\$:a::ba:\$1b\$:a:ab::\$1",
            "noMatches" to "abc",
            "emptyReceiver" to "\$",
            "replacementIsNotSearchedAgain" to "aaaa",
        )

        cases.forEach { (methodName, expected) ->
            val (values, events) = analyze(methodName)

            assertEquals(expected, assertIs<TsTestValue.TsString>(values.single()).value, methodName)
            assertTrue(events.any { (it.decision as? TsUnknownCallDecision.ModelApplied)?.modelId == MODEL_ID })
            assertTrue(events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
        }
    }

    @Test
    fun `non string arguments remain explicit residual calls`() {
        val cases = listOf(
            "numericSearchIsResidual",
            "numericReplacementIsResidual",
            "callbackReplacementIsResidual",
            "callableSearchIsResidual",
        )

        for (methodName in cases) {
            val (values, events) = analyze(methodName)

            assertTrue(values.isEmpty(), methodName)
            assertEquals(TsUnknownCallOutcome.PATH_STOPPED, events.single().outcome, methodName)
        }
    }

    private fun analyze(methodName: String): Pair<List<TsTestValue>, List<TsUnknownCallEvent>> {
        val method = scene.projectClasses.single { it.name == "StringReplaceAllEtsIr" }.methods
            .single { it.name == methodName }
        val events = mutableListOf<TsUnknownCallEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                events += event
            }
        }

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(setOf(MODEL_ID))),
            observer = observer,
        ).use { machine ->
            val values = machine.analyze(listOf(method)).map { state ->
                TsTestResolver().resolve(method, state).returnValue
            }
            values to events.toList()
        }
    }

    private companion object {
        const val MODEL_ID = "ts.string.replaceAll"
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
