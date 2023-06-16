package org.usvm.samples.codegen

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

import org.usvm.test.util.checkers.eq


class VoidStaticMethodsTest : JavaMethodTestRunner() {
    @Test
    fun testInvokeChangeStaticFieldMethod() {
        checkExecutionMatches(
            VoidStaticMethodsTestingClass::invokeChangeStaticFieldMethod,
            eq(2),
        )
    }

    @Test
    fun testInvokeThrowExceptionMethod() {
        checkExecutionMatches(
            VoidStaticMethodsTestingClass::invokeThrowExceptionMethod,
            eq(3),
        )
    }

    @Test
    fun testInvokeAnotherThrowExceptionMethod() {
        checkExecutionMatches(
            VoidStaticMethodsTestingClass::invokeAnotherThrowExceptionMethod,
            eq(2),
        )
    }
}