package org.usvm.samples.mixed

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

internal class SerializableExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Only 1 execution - NPE")
    fun testExample() {
        checkDiscoveredPropertiesWithExceptions(
            SerializableExample::example,
            eq(1),
            { _, r -> r.isSuccess }
        )
    }
}
