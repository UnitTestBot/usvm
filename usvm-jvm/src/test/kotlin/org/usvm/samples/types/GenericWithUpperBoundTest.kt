package org.usvm.samples.types

import org.junit.jupiter.api.Disabled
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import kotlin.test.Test

internal class GenericWithUpperBoundTest : JavaMethodTestRunner() {
    @Test
    @Disabled("We do not support generics, so we may generate `GenericWithUpperBound<String>` here that leads to fail")
    fun testExcludeComparable() {
        checkDiscoveredProperties(
            GenericWithUpperBound<Int>::excludeComparable,
            eq(2),
            { _, value, r -> value == null && r == 0 },
            { _, value, r -> value != null && r == 1 },
        )
    }
}