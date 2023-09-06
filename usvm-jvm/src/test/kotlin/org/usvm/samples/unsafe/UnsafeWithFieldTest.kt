package org.usvm.samples.unsafe

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class UnsafeWithFieldTest : JavaMethodTestRunner() {

    @Test
    @Disabled("Complex logic with class instances")
    fun checkSetField() {
        checkDiscoveredProperties(
            UnsafeWithField::setField,
            eq(1)
        )
    }
}