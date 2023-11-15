package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

internal class ObjectExampleTest : JavaMethodTestRunner() {
    @Test
    fun testObjectToStringVirtualInvoke() {
        checkDiscoveredProperties(
            ObjectExample::objectToStringVirtualInvokeExample,
            ge(2),
            { _, x, r -> x == null && r == null },
            { _, x, _ -> x != null },
        )
    }
}
