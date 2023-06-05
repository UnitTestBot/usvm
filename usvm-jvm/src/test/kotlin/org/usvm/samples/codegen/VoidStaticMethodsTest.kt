package org.usvm.samples.codegen

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class VoidStaticMethodsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@41ccb3b9")
    fun testInvokeChangeStaticFieldMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeChangeStaticFieldMethod,
            eq(2),
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testInvokeThrowExceptionMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeThrowExceptionMethod,
            eq(3),
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testInvokeAnotherThrowExceptionMethod() {
        checkDiscoveredProperties(
            VoidStaticMethodsTestingClass::invokeAnotherThrowExceptionMethod,
            eq(2),
        )
    }
}