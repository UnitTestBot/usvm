package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

class TestStatics : JavaMethodTestRunner() {
    @Test
    fun `Test static field access`() {
        checkDiscoveredProperties(
            ObjectWithStatics::staticsAreEqual,
            eq(1),
            { _, r -> r == 0 },
        )
    }

    @Test
    fun `Test static field write`() {
        checkDiscoveredProperties(
            ObjectWithStatics::mutateStatics,
            eq(1),
            { _, r -> r == 1 },
        )
    }

    @Test
    fun `Test mutable primitive static field`() {
        checkDiscoveredProperties(
            ObjectWithStatics::useMutablePrimitiveStaticField,
            eq(2),
            { _, r -> r == 0 },
            { _, r -> r == 1 },
        )
    }

    @Test
    fun `Test final primitive static field`() {
        checkDiscoveredProperties(
            ObjectWithStatics::useFinalPrimitiveStaticField,
            eq(1),
            { _, r -> r == 0 },
        )
    }
}
