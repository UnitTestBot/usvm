package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class AbstractAnonymousClassTest : JavaMethodTestRunner() {
    @Test
    @Disabled("java.lang.InstantiationException: org.usvm.samples.objects.AbstractAnonymousClass")
    fun testNonOverriddenMethod() {
        checkDiscoveredProperties(
            AbstractAnonymousClass::methodWithoutOverrides,
            eq(1)
        )
    }

    @Test
    @Disabled("Unexpected exception type thrown ==> expected: <org.opentest4j.AssertionFailedError> but was: <java.lang.InstantiationException>")
    fun testOverriddenMethod() {
        // check we have error during execution
        assertThrows<org.opentest4j.AssertionFailedError> {
            checkDiscoveredProperties(
                AbstractAnonymousClass::methodWithOverride,
                eq(0)
            )
        }
    }
}