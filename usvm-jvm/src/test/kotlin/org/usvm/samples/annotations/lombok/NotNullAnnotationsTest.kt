package org.usvm.samples.annotations.lombok

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner


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
        checkExecutionMatches(
            NotNullAnnotations::lombokNonNull,
            { _, value, r -> value == r },
        )
    }
}