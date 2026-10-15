package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsEntryStartupInputsTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                path = getResourcePath("/models/EntryStartupInputs.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )
    private val machineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = 5.seconds,
        solverType = SolverType.YICES,
        throwExceptionOnStepFailure = true,
    )

    @Test
    fun `unsupported entry inputs do not discard supported methods of the same class`() {
        val supported = getMethod(methodName = "supported", className = "EntryStartupInputs")
        val methods = unsupportedMethods() + supported
        val configured = mutableListOf<String>()

        val result = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = methods) { method, _ -> configured += method.name }
        }

        assertFalse(result.engineFailed)
        assertEquals(listOf("supported"), configured)
        assertTrue(result.states.isNotEmpty())
        assertTrue(result.states.all { it.entrypoint == supported })
        assertEquals(3, result.unsupportedPaths.size)
        assertTrue(result.unsupportedPaths.any { "Callable input parameter 'reader'" in it })
        assertTrue(result.unsupportedPaths.any { "withBigInt" in it && "BigInt input parameter 'value'" in it })
        assertTrue(result.unsupportedPaths.any { "withGenericBigInt" in it && "BigInt input parameter 'value'" in it })

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = supported,
            { _, value -> value eq 1 },
            { _, value -> value eq 2 },
            invariants = arrayOf({ _, value -> (value eq 1) || (value eq 2) }),
        )
    }

    @Test
    fun `an entirely unsupported entry set returns explicit per-method reasons`() {
        val methods = unsupportedMethods()

        val result = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = methods)
        }

        assertFalse(result.engineFailed)
        assertTrue(result.states.isEmpty())
        assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
        assertEquals(3, result.unsupportedPaths.size)
        for (method in methods) {
            assertTrue(result.unsupportedPaths.any { method.name in it })
        }
    }

    @Test
    fun `unexpected initial-state configuration errors remain failures`() {
        val method = getMethod(methodName = "supported", className = "EntryStartupInputs")

        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            val exception = assertFailsWith<IllegalStateException> {
                machine.analyzeWithMetadata(methods = listOf(method)) { _, _ ->
                    error("Unexpected configuration defect")
                }
            }

            assertEquals("Unexpected configuration defect", exception.message)
        }
    }

    @Test
    fun `an infeasible entry is distinct from unsupported inputs and does not discard another entry`() {
        val infeasible = getMethod(methodName = "supported", className = "EntryStartupInputs")
        val independent = getMethod(methodName = "independent", className = "EntryStartupInputs")
        val methods = unsupportedMethods() + infeasible + independent

        val result = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = methods) { method, state ->
                // Deliberately test the initial solver's UNSAT contract, independently of source feasibility.
                if (method == infeasible) {
                    state.pathConstraints += state.ctx.falseExpr
                }
            }
        }

        assertFalse(result.engineFailed)
        assertTrue(result.states.isNotEmpty())
        assertTrue(result.states.all { it.entrypoint == independent })
        assertEquals(3, result.unsupportedPaths.size)
        assertEquals(1, result.infeasibleEntryPoints.size)
        assertTrue("supported" in result.infeasibleEntryPoints.single())
    }

    @Test
    fun `an entirely infeasible entry set is exhausted without an unsupported or engine failure`() {
        val method = getMethod(methodName = "supported", className = "EntryStartupInputs")

        val result = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method)) { _, state ->
                state.pathConstraints += state.ctx.falseExpr
            }
        }

        assertFalse(result.engineFailed)
        assertFalse(result.runtimeLimited)
        assertTrue(result.states.isEmpty())
        assertTrue(result.unsupportedPaths.isEmpty())
        assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
        assertEquals(1, result.infeasibleEntryPoints.size)
        assertTrue("supported" in result.infeasibleEntryPoints.single())
    }

    private fun unsupportedMethods() = listOf("withCallback", "withBigInt", "withGenericBigInt").map { name ->
        getMethod(methodName = name, className = "EntryStartupInputs")
    }
}
