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
            eq(1)
        )
    }

    @Test
    fun testOverriddenMethod() {
        // check we have error during execution
        assertThrows<org.opentest4j.AssertionFailedError> {
            checkExecutionMatches(
                AbstractAnonymousClass::methodWithOverride,
                eq(0)
            )
        }
    }
}