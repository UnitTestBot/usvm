package org.usvm.samples.unsafe

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.unsafe.UnsafeWithField
import org.usvm.test.util.checkers.eq


internal class UnsafeWithFieldTest: JavaMethodTestRunner() {

    @Test
    @Disabled("Not implemented: string constant")
    fun checkSetField() {
        checkDiscoveredProperties(
            UnsafeWithField::setField,
            eq(1)
        )
    }
}