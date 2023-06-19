package org.usvm.samples.codegen.deepequals

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


import org.usvm.test.util.checkers.eq


class ClassWithNullableFieldTest : JavaMethodTestRunner() {
    @Test
    fun testClassWithNullableFieldInCompound() {
        checkExecutionMatches(
            ClassWithNullableField::returnCompoundWithNullableField,
        )
    }

    @Test
    fun testClassWithNullableFieldInGreatCompound() {
        checkExecutionMatches(
            ClassWithNullableField::returnGreatCompoundWithNullableField,
        )
    }
}