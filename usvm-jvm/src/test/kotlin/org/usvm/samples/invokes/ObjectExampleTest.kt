package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

internal class ObjectExampleTest : JavaMethodTestRunner() {
    @Test
    fun testObjectToStringVirtualInvoke() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectExample::objectToStringVirtualInvokeExample,
            ge(2),
        )
    }
}
