package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UPathSelector
import org.usvm.api.TsTestValue
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TsMachineForkLimitTest : TsMethodTestRunner() {
    private val source = getResourcePath("/models/MachineForkLimit.ts")
    private val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
    override val scene = EtsScene(listOf(file))

    init {
        options = options.copy(
            stopOnCoverage = 0,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `fork limit reports the exact reason and leaves source prefixes pending`() {
        val method = getMethod(methodName = "binary", className = "MachineForkLimit")
        var selector: UPathSelector<TsState>? = null
        val machineOptions = options.copy(forkLimit = 1UL)

        TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            machineObserverFactory = { queue ->
                selector = queue
                object : UMachineObserver<TsState> {}
            },
        ).use { machine ->
            val result = machine.analyzeWithOutcome(methods = listOf(method))
            val pending = requireNotNull(selector).drain().toList()

            assertEquals(TsAnalysisStopReason.FORK_LIMIT, result.stopReason)
            assertEquals(expected = 1UL, actual = result.generatedForks)
            assertEquals(expected = 1UL, actual = result.forkLimit)
            assertTrue(result.states.isEmpty())
            assertTrue(result.unsupportedPaths.isEmpty())
            assertEquals(expected = 2, actual = pending.size)
            assertTrue(pending.all { it.callStack.isNotEmpty() })
            assertTrue(pending.all { it.unsupportedReason == null && it.engineFailureReason == null })
            assertTrue(pending.none { it.pathConstraints.isFalse })
        }
    }

    @Test
    fun `provider-pruned infeasible alternatives do not spend the fork budget`() {
        val method = getMethod(methodName = "infeasible", className = "MachineForkLimit")

        val result = TsMachine(scene = scene, options = options.copy(forkLimit = 2UL), tsOptions = TsOptions())
            .use { machine -> machine.analyzeWithOutcome(methods = listOf(method)) }
        val tests = result.states.map { state -> TsTestResolver().resolve(method, state) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
        assertEquals(expected = 1UL, actual = result.generatedForks)
        assertEquals(expected = 2, actual = tests.size)
        assertTrue(
            tests.all { test ->
                val flag = test.before.parameters.single() as TsTestValue.TsBoolean
                val value = test.returnValue as TsTestValue.TsNumber
                value.number == if (flag.value) 1.0 else 2.0
            },
        )
        assertTrue(result.unsupportedPaths.isEmpty())
    }
}
