package org.usvm.samples.codegen

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class VoidStaticMethodsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@206987b2")
    fun testInvokeChangeStaticFieldMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeChangeStaticFieldMethod,
            eq(2),
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3d2bcab9")
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