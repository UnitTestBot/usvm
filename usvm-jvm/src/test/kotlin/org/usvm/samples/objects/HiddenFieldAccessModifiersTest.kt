package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class HiddenFieldAccessModifiersTest : JavaMethodTestRunner() {
    @Test
    fun testCheckSuperFieldEqualsOne() {
        checkExecutionMatches(
            HiddenFieldAccessModifiersExample::checkSuperFieldEqualsOne,
            eq(3),
            { _, o, _ -> o == null },
            { _, _, r -> r == true },
            { _, _, r -> r == false },
        )
    }
}