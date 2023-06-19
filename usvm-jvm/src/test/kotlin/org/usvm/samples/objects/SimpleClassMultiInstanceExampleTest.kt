package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimpleClassMultiInstanceExampleTest : JavaMethodTestRunner() {
    @Test
    fun singleObjectChangeTest() {
        checkExecutionMatches(
            SimpleClassMultiInstanceExample::singleObjectChange,
            { _, first, _, _ -> first == null }, // NPE
            { _, first, _, r -> first.a < 5 && r == 3 },
            { _, first, _, r -> first.a >= 5 && r == first.b },
        )
    }
}