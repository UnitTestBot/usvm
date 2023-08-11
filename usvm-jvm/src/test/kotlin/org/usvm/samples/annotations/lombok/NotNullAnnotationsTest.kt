package org.usvm.samples.annotations.lombok

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


/**
 * Tests for Lombok NonNull annotation
 *
 * We do not calculate coverage here as Lombok always make it pure
 * (see, i.e. https://stackoverflow.com/questions/44584487/improve-lombok-data-code-coverage)
 * and Lombok code is considered to be already tested itself.
 */
internal class NotNullAnnotationsTest : JavaMethodTestRunner() {
    @Test
    fun testNonNullAnnotations() {
        checkDiscoveredProperties(
            NotNullAnnotations::lombokNonNull,
            /*eq(1)*/ignoreNumberOfAnalysisResults,
            { _, value, r -> value == r },
            // TODO support NotNull annotations
        )
    }
}