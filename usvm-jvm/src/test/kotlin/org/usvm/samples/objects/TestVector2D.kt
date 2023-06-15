package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestVector2D : JavaMethodTestRunner() {
    @Test
    fun `Test isPerpendicularTo`() {
        checkWithExceptionPropertiesMatches(
            Vector2D::isPerpendicularTo,
            ignoreNumberOfAnalysisResults,
            { _, b, r -> b == null && r.exceptionOrNull() is NullPointerException },
            { a, b, r -> r.getOrNull() == true && a.isPerpendicularTo(b) },
            { a, b, r -> r.getOrNull() == false && !a.isPerpendicularTo(b) },
        )
    }


    @Test
    fun `Test isCollinearTo`() {
        checkWithExceptionPropertiesMatches(
            Vector2D::isCollinearTo,
            ignoreNumberOfAnalysisResults,
            { _, b, r -> b == null && r.exceptionOrNull() is NullPointerException },
            { a, b, r -> r.getOrNull() == true && a.isCollinearTo(b) },
            { a, b, r -> r.getOrNull() == false && !a.isCollinearTo(b) },
        )
    }
}