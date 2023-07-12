package org.usvm.samples.reflection

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class NewInstanceExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: string constant")
    fun testNewInstanceExample() {
        checkDiscoveredProperties(
            NewInstanceExample::createWithReflectionExample,
            eq(1),
            { _, r -> r == 0 }
        )
    }
}
