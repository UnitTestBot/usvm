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
    fun `Test static initializer`() {
        checkDiscoveredProperties(
            ObjectWithStatics::staticsInitialized,
            eq(1),
            { _, r -> r == 0 },
        )
    }
}
