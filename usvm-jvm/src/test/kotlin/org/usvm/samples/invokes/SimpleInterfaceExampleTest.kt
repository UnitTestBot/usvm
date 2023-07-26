package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimpleInterfaceExampleTest : JavaMethodTestRunner() {
    @Test
    fun testOverrideMethod() {
        checkDiscoveredProperties(
            SimpleInterfaceExample::overrideMethod,
            eq(3),
            { _, o, _, _ -> o == null },
            { _, o, v, r -> o is SimpleInterfaceImpl && r == v + 2 },
            { _, o, v, r -> o is Realization && r == v + 5 }
        )
    }

    @Test
    fun testDefaultMethod() {
        checkDiscoveredProperties(
            SimpleInterfaceExample::defaultMethod,
            eq(2),
            { _, o, _, _ -> o == null },
            { _, o, v, r -> o != null && r == v - 5 }
        )
    }

    @Test
    fun testInvokeMethodFromImplementor() {
        checkDiscoveredProperties(
            SimpleInterfaceExample::invokeMethodFromImplementor,
            eq(2),
            { _, o, _ -> o == null },
            { _, o, r -> o != null && r == 10 },
        )
    }
}