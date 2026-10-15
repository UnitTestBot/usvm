package org.usvm.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilNumberComparisonTest {
    @Test
    fun `equal infinities compare equal`() {
        assertTrue(Double.POSITIVE_INFINITY eq Double.POSITIVE_INFINITY)
        assertTrue(Double.NEGATIVE_INFINITY eq Double.NEGATIVE_INFINITY)
        assertFalse(Double.POSITIVE_INFINITY eq Double.NEGATIVE_INFINITY)
        assertFalse(Double.POSITIVE_INFINITY neq Double.POSITIVE_INFINITY)
    }

    @Test
    fun `NaN stays unequal and finite tolerance stays unchanged`() {
        assertFalse(Double.NaN eq Double.NaN)
        assertTrue(Double.NaN neq Double.NaN)
        assertTrue(0.0 eq Double.MIN_VALUE)
        assertFalse(0.0 eq 1.0)
    }

    @Test
    fun `generic equality follows JavaScript zero equality`() {
        assertTrue(0.0 eq -0.0)
        assertFalse(0.0 neq -0.0)
    }
}
