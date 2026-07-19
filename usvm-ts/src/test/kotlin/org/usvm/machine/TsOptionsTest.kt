package org.usvm.machine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TsOptionsTest {
    @Test
    fun `symbolic progress stopping is opt in`() {
        val options = TsOptions()

        assertFalse(options.symbolicProgressStop)
        assertNull(options.progressTimeout)
        assertFalse(options.tsTargetReachabilityPruning)
        assertFalse(options.moduleRuntimeModel)
        assertFalse(options.callableValueModel)
        assertFalse(options.iteratorModel)
        assertFalse(options.exactCollectionBuiltins)
    }

    @Test
    fun `semantic feature groups are independently opt in`() {
        assertEquals(
            listOf(true, false, false, false),
            TsOptions(moduleRuntimeModel = true).semanticFeatureVector(),
        )
        assertEquals(
            listOf(false, true, false, false),
            TsOptions(callableValueModel = true).semanticFeatureVector(),
        )
        assertEquals(
            listOf(false, false, true, false),
            TsOptions(iteratorModel = true).semanticFeatureVector(),
        )
        assertEquals(
            listOf(false, false, false, true),
            TsOptions(exactCollectionBuiltins = true).semanticFeatureVector(),
        )
    }

    @Test
    fun `enabled progress stopping requires a positive finite duration`() {
        assertFailsWith<IllegalArgumentException> {
            TsOptions(symbolicProgressStop = true)
        }
        assertFailsWith<IllegalArgumentException> {
            TsOptions(symbolicProgressStop = true, progressTimeout = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            TsOptions(symbolicProgressStop = true, progressTimeout = Duration.INFINITE)
        }

        val options = TsOptions(symbolicProgressStop = true, progressTimeout = 3.seconds)
        assertEquals(3.seconds, options.progressTimeout)
    }
}

private fun TsOptions.semanticFeatureVector(): List<Boolean> = listOf(
    moduleRuntimeModel,
    callableValueModel,
    iteratorModel,
    exactCollectionBuiltins,
)
