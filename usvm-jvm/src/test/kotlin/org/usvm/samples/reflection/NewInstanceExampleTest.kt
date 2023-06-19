package org.usvm.samples.reflection

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class NewInstanceExampleTest : JavaMethodTestRunner() {
    @Test
    fun testNewInstanceExample() {
        checkExecutionMatches(
            NewInstanceExample::createWithReflectionExample,
            { _, r -> r == 0 }
        )
    }
}
