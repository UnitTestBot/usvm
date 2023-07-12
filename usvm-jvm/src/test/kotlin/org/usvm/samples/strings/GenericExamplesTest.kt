package org.usvm.samples.strings

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

internal class GenericExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Expected exactly 2 executions, but 1 found. Support strings")
    fun testContainsOkWithIntegerType() {
        checkDiscoveredPropertiesWithExceptions(
            GenericExamples<Int>::containsOk,
            eq(2),
            { _, obj, result -> obj == null && result.isException<NullPointerException>() },
            { _, obj, result -> obj != null && result.isSuccess && result.getOrNull() == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testContainsOkExampleTest() {
        checkDiscoveredProperties(
            GenericExamples<String>::containsOkExample,
            eq(1),
            { _, result -> result == true }
        )
    }
}
