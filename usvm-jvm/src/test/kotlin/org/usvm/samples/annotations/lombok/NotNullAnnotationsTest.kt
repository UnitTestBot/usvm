package org.usvm.samples.annotations.lombok

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


/**
 * Tests for Lombok NonNull annotation
 *
 * We do not calculate coverage here as Lombok always make it pure
 * (see, i.e. https://stackoverflow.com/questions/44584487/improve-lombok-data-code-coverage)
 * and Lombok code is considered to be already tested itself.
 */
internal class NotNullAnnotationsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Can not set static final int field java.lang.Integer.MIN_VALUE to java.lang.Integer")
    fun testNonNullAnnotations() {
        checkDiscoveredProperties(
            NotNullAnnotations::lombokNonNull,
            eq(1),
            { _, value, r -> value == r },
        )
    }
}