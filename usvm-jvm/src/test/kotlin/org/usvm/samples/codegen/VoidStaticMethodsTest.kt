package org.usvm.samples.codegen

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class VoidStaticMethodsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: string constant")
    fun testInvokeChangeStaticFieldMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeChangeStaticFieldMethod,
            eq(2),
        )
    }

    @Test
    @Disabled("Expected exactly 3 executions, but 1 found")
    fun testInvokeThrowExceptionMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeThrowExceptionMethod,
            eq(3),
        )
    }

    @Test
    @Disabled("Expected exactly 2 executions, but 1 found. Tune coverage ZONE")
    fun testInvokeAnotherThrowExceptionMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeAnotherThrowExceptionMethod,
            eq(2),
        )
    }
}