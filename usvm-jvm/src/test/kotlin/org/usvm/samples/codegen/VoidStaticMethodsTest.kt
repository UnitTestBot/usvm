package org.usvm.samples.codegen

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class VoidStaticMethodsTest : JavaMethodTestRunner() {
    @Test
    fun testInvokeChangeStaticFieldMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeChangeStaticFieldMethod,
            eq(2),
        )
    }

    @Test
    fun testInvokeThrowExceptionMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeThrowExceptionMethod,
            eq(3),
        )
    }

    @Test
    fun testInvokeAnotherThrowExceptionMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeAnotherThrowExceptionMethod,
            eq(2),
        )
    }
}