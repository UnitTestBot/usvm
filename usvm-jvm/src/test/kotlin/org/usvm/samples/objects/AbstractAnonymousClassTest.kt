package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class AbstractAnonymousClassTest : JavaMethodTestRunner() {
    @Test
    fun testNonOverriddenMethod() {
        checkExecutionMatches(
            AbstractAnonymousClass::methodWithoutOverrides,
        )
    }

    @Test
    fun testOverriddenMethod() {
        // check we have error during execution
        assertThrows<org.opentest4j.AssertionFailedError> {
            checkPropertiesMatches(
                AbstractAnonymousClass::methodWithOverride,
                eq(0)
            )
        }
    }
}