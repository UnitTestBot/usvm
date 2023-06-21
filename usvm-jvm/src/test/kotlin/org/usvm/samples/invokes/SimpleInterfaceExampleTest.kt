package org.usvm.samples.invokes

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimpleInterfaceExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("java.lang.InstantiationException: org.usvm.samples.invokes.SimpleInterface")
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
    @Disabled("java.lang.InstantiationException: org.usvm.samples.invokes.SimpleInterface")
    fun testDefaultMethod() {
        checkDiscoveredProperties(
            SimpleInterfaceExample::defaultMethod,
            eq(2),
            { _, o, _, _ -> o == null },
            { _, o, v, r -> o != null && r == v - 5 }
        )
    }

    @Test
    @Disabled("Sequence is empty.")
    fun testInvokeMethodFromImplementor() {
        checkDiscoveredProperties(
            SimpleInterfaceExample::invokeMethodFromImplementor,
            eq(2),
            { _, o, _ -> o == null },
            { _, o, r -> o != null && r == 10 },
        )
    }
}