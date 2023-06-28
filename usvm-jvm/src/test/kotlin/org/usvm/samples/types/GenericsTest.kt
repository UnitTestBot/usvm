package org.usvm.samples.types

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class GenericsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: String constants")
    fun mapAsParameterTest() {
        checkDiscoveredProperties(
            Generics<*>::mapAsParameter,
            eq(2),
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    @Disabled("https://github.com/UnitTestBot/UTBotJava/issues/1620 wrong equals")
    fun genericAsFieldTest() {
        checkDiscoveredProperties(
            Generics<*>::genericAsField,
            ignoreNumberOfAnalysisResults,
            { _, obj, r -> obj?.field == null && r == false },
            // we can cover this line with any of these two conditions
            { _, obj, r -> (obj.field != null && obj.field != "abc" && r == false) || (obj.field == "abc" && r == true) },
        )
    }

    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
    fun mapAsStaticFieldTest() {
        checkDiscoveredProperties(
            Generics<*>::mapAsStaticField,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == "value" },
        )
    }

    @Test
    @Disabled("Not implemented: String constants")
    fun mapAsNonStaticFieldTest() {
        checkDiscoveredProperties(
            Generics<*>::mapAsNonStaticField,
            ignoreNumberOfAnalysisResults,
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    @Disabled("Not implemented: String constants")
    fun methodWithRawTypeTest() {
        checkDiscoveredProperties(
            Generics<*>::methodWithRawType,
            eq(2),
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    @Disabled("Expected exactly 1 executions, but 2 found. Same exception discovered multiple times")
    fun testMethodWithArrayTypeBoundary() {
        checkDiscoveredPropertiesWithExceptions(
            Generics<*>::methodWithArrayTypeBoundary,
            eq(1),
            { _, r -> r.exceptionOrNull() is java.lang.NullPointerException },
        )
    }
}