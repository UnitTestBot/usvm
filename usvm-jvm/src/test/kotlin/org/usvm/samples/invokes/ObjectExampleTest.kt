package org.usvm.samples.invokes

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

internal class ObjectExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("TODO flaky https://github.com/UnitTestBot/jacodb/issues/197")
    fun testObjectToStringVirtualInvoke() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectExample::objectToStringVirtualInvokeExample,
            ge(2),
        )
    }
}
