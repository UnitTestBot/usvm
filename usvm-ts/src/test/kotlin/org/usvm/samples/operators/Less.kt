package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.toDouble
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class Less : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Less.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun testLessNumbers() {
        val method = getMethod("lessNumbers")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, r -> a.number < b.number && (r eq a) },
            { a, b, r -> b.number < a.number && (r eq b) },
            { a, b, r -> a.number == b.number && (r eq 0) },
        )
    }

    @Test
    fun testLessBooleans() {
        val method = getMethod("lessBooleans")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsBoolean>(
            method,
            { a, b, r -> !a.value && b.value && !r.value },
            { a, b, r -> a.value && !b.value && !r.value },
            { a, b, r -> a.value == b.value && !r.value },
        )
    }

    @Test
    fun testLessMixed() {
        val method = getMethod("lessMixed")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, b, r -> a.number < b.value.toDouble() && (r eq a) },
            { a, b, r -> b.value.toDouble() < a.number && (r.number == b.value.toDouble()) },
            { a, b, r -> a.number == b.value.toDouble() && (r eq 0) },
        )
    }

    @Test
    fun `object comparison reports unmodeled primitive conversion`() {
        val method = getMethod("lessRefs")
        val analysisOptions = options.copy(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            throwExceptionOnStepFailure = false,
        )

        val outcome = TsMachine(scene = scene, options = analysisOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertEquals(
            expected = listOf("Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion"),
            actual = outcome.unsupportedPaths,
        )
        assertFalse(outcome.timedOut)
        assertFalse(outcome.unsupportedCall)
        assertFalse(outcome.engineFailed)
    }

    @Test
    fun testLessUnknown() {
        val method = getMethod("lessUnknown")
        discoverProperties<TsTestValue.TsUnknown, TsTestValue.TsUnknown, TsTestValue.TsBoolean>(
            method,
        )
    }
}
