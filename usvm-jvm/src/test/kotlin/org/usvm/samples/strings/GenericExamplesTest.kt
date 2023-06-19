package org.usvm.samples.strings

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

internal class GenericExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testContainsOkWithIntegerType() {
        checkWithExceptionExecutionMatches(
            GenericExamples<Int>::containsOk,
            { _, obj, result -> obj == null && result.isException<NullPointerException>() },
            { _, obj, result -> obj != null && result.isSuccess && result.getOrNull() == false }
        )
    }

    @Test
    fun testContainsOkExampleTest() {
        checkExecutionMatches(
            GenericExamples<String>::containsOkExample,
            { _, result -> result == true }
        )
    }
}
