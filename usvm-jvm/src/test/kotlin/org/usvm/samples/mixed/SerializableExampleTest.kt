package org.usvm.samples.mixed

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SerializableExampleTest : JavaMethodTestRunner() {

    @Test
    @Disabled("Not implemented: string constant")
    fun testExample() {
        checkDiscoveredProperties(
            SerializableExample::example,
            eq(1),
        )
    }
}
