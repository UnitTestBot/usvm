package org.usvm.samples.unsafe

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


internal class UnsafeWithFieldTest: JavaMethodTestRunner() {

    @Test
    fun checkSetField() = disableTest("Expected exactly 1 executions, but 0 found") {
        checkDiscoveredProperties(
            UnsafeWithField::setField,
            eq(1)
        )
    }
}