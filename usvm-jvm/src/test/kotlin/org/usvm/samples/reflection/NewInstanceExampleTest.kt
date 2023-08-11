package org.usvm.samples.reflection

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


class NewInstanceExampleTest : JavaMethodTestRunner() {
    @Test
    fun testNewInstanceExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            NewInstanceExample::createWithReflectionExample,
            eq(1),
            { _, r -> r == 0 }
        )
    }
}
