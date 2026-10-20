package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Normal source entries; inspect scalar returns because native input witnesses are not serializable yet. */
class InputCollectionsTest : TsMethodTestRunner() {
    override val scene = loadScene("/models/InputCollections.ts")
    override val tsOptions = TsOptions(eraseTypeAssertions = true, maxInputCollectionSize = 2, maxArraySize = 4)

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            collectedStatesLimit = null,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `input map includes empty and nonempty cardinalities`() = assertReturns("mapPresence", setOf(1.0, 2.0, 3.0))

    @Test
    fun `input map membership values size and mutations agree`() = assertReturns("mapMutation", setOf(1.0, 2.0))

    @Test
    fun `symbolically equal receivers share a backend and distinct receivers remain separate`() =
        assertReturns("aliases", setOf(1.0, 2.0))

    @Test
    fun `typed input field and local alias share mutations`() = assertReturns("field", setOf(1.0))

    @Test
    fun `input set includes present and absent elements with coherent size`() =
        assertReturns("setPresence", setOf(1.0, 2.0))

    @Test
    fun `input insertion order enumerates every live entry exactly once`() =
        assertReturns("iteration", setOf(1.0, 2.0))

    @Test
    fun `input iterator observes clear and reinsert with sticky exhaustion`() =
        assertReturns("liveIteration", setOf(1.0))

    @Test
    fun `input string keys preserve SameValueZero content equality`() = assertReturns("stringKeys", setOf(1.0))

    @Test
    fun `input mutation does not leak between forks`() = assertReturns("stateFork", setOf(1.0, 2.0))

    @Test
    fun `initial lookup is stable and membership distinguishes missing values`() =
        assertReturns("initialLookup", setOf(1.0, 2.0, 3.0))

    @Test
    fun `erased aliases retain native collection state`() = assertReturns("erasedAlias", setOf(1.0))

    @Test
    fun `native size writes and deletes stay explicit unsupported boundaries`() {
        for (name in listOf("invalidSizeWrite", "invalidSizeDelete")) {
            val method = getMethod(methodName = name, className = "InputCollections")

            val outcome = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
            }

            assertFalse(outcome.engineFailed, name)
            assertTrue(outcome.unsupportedPaths.any { it.contains("native collection size") }, name)
            assertTrue(outcome.states.isEmpty(), name)
        }
    }

    @Test
    fun `disabled input domain retains explicit unsupported outcome`() {
        val disabled = tsOptions.copy(maxInputCollectionSize = null)
        for (name in listOf("mapMutation", "field")) {
            val method = getMethod(methodName = name, className = "InputCollections")

            val outcome = TsMachine(scene = scene, options = options, tsOptions = disabled).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
            }

            assertTrue(outcome.unsupportedCall, name)
            val completed = outcome.states.any { it.methodResult is TsMethodResult.Success }
            assertFalse(completed, name)
            assertFalse(outcome.engineFailed, name)
        }
    }

    private fun assertReturns(name: String, expected: Set<Double>) {
        val method = getMethod(methodName = name, className = "InputCollections")
        val outcome = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertFalse(outcome.engineFailed, name)
        assertFalse(outcome.unsupportedCall, outcome.unsupportedPaths.toString())
        val actual = outcome.states.map { state ->
            val result = state.methodResult as TsMethodResult.Success
            state.models.first().eval(result.value.asExpr(state.ctx.fp64Sort)).extractDouble()
        }.toSet()
        assertEquals(expected = expected, actual = actual, message = name)
    }
}
