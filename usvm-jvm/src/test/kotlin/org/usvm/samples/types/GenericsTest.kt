package org.usvm.samples.types

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class GenericsTest : JavaMethodTestRunner() {
    @Test
    fun mapAsParameterTest() {
        checkExecutionMatches(
            Generics<*>::mapAsParameter,
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    @Disabled("https://github.com/UnitTestBot/UTBotJava/issues/1620 wrong equals")
    fun genericAsFieldTest() {
        checkExecutionMatches(
            Generics<*>::genericAsField,
            { _, obj, r -> obj?.field == null && r == false },
            // we can cover this line with any of these two conditions
            { _, obj, r -> (obj.field != null && obj.field != "abc" && r == false) || (obj.field == "abc" && r == true) },
        )
    }

    @Test
    fun mapAsStaticFieldTest() {
        checkExecutionMatches(
            Generics<*>::mapAsStaticField,
            { _, r -> r == "value" },
        )
    }

    @Test
    fun mapAsNonStaticFieldTest() {
        checkExecutionMatches(
            Generics<*>::mapAsNonStaticField,
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    fun methodWithRawTypeTest() {
        checkExecutionMatches(
            Generics<*>::methodWithRawType,
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    fun testMethodWithArrayTypeBoundary() {
        checkWithExceptionExecutionMatches(
            Generics<*>::methodWithArrayTypeBoundary,
            { _, r -> r.exceptionOrNull() is java.lang.NullPointerException },
        )
    }
}