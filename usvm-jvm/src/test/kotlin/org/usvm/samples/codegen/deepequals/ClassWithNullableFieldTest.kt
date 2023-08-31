package org.usvm.samples.codegen.deepequals

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


import org.usvm.test.util.checkers.eq


class ClassWithNullableFieldTest : JavaMethodTestRunner() {
    @Test
    fun testClassWithNullableFieldInCompound() {
        checkDiscoveredProperties(
            ClassWithNullableField::returnCompoundWithNullableField,
            eq(2),
        )
    }

    @Test
    fun testClassWithNullableFieldInGreatCompound() {
        checkDiscoveredProperties(
            ClassWithNullableField::returnGreatCompoundWithNullableField,
            eq(3),
        )
    }
}