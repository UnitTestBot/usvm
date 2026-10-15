package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks configured machine entry constraints, not TypeScript source reachability. */
class TsInitialStateFeasibilityTest : TsMethodTestRunner() {
    private val file = loadEtsFileAutoConvert(
        getResourcePath("/models/SymbolicStringEngine.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    override val scene = EtsScene(listOf(file))

    @Test
    fun `infeasible configured entry is pruned without an engine failure`() {
        val method = getMethod(methodName = "length", className = "SymbolicStringEngine")

        val analysis = analyzeWithInfeasibleEntry(methods = listOf(method), infeasibleMethod = method)

        assertEquals(emptyList(), analysis.states)
        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertFalse(analysis.timedOut)
        assertFalse(analysis.unsupportedCall)
        assertFalse(analysis.engineFailed)
        assertTrue(analysis.unsupportedPaths.isEmpty())
    }

    @Test
    fun `infeasible entry does not discard another method`() {
        val infeasibleMethod = getMethod(methodName = "length", className = "SymbolicStringEngine")
        val feasibleMethod = getMethod(methodName = "anyAliasLength", className = "SymbolicStringEngine")

        val analysis = analyzeWithInfeasibleEntry(
            methods = listOf(infeasibleMethod, feasibleMethod),
            infeasibleMethod = infeasibleMethod,
        )

        assertTrue(analysis.states.isNotEmpty())
        assertTrue(analysis.states.all { state -> state.entrypoint == feasibleMethod })
        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertFalse(analysis.timedOut)
        assertFalse(analysis.unsupportedCall)
        assertFalse(analysis.engineFailed)
        assertTrue(analysis.unsupportedPaths.isEmpty())
    }

    private fun analyzeWithInfeasibleEntry(
        methods: List<EtsMethod>,
        infeasibleMethod: EtsMethod,
    ): TsMachineAnalysisResult {
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            removeCoveredEntryPoints = false,
        )

        return TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithMetadata(
                methods = methods,
                configureInitialState = { method, state ->
                    if (method == infeasibleMethod) {
                        state.pathConstraints += state.ctx.falseExpr
                    }
                },
            )
        }
    }
}
