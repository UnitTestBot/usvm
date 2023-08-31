package org.usvm.samples.types

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class GenericsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: string constant")
    fun mapAsParameterTest() {
        checkDiscoveredProperties(
            Generics<*>::mapAsParameter,
            eq(2),
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    @Disabled("org.jacodb.impl.fs.ByteCodeConverterKt: java.lang.OutOfMemoryError: Java heap space")
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
    @Disabled("Not implemented: string constant")
    fun mapAsStaticFieldTest() {
        checkDiscoveredProperties(
            Generics<*>::mapAsStaticField,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == "value" },
        )
    }

    @Test
    @Disabled("Not implemented: string constant")
    fun mapAsNonStaticFieldTest() {
        checkDiscoveredProperties(
            Generics<*>::mapAsNonStaticField,
            ignoreNumberOfAnalysisResults,
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    @Disabled("Not implemented: string constant")
    fun methodWithRawTypeTest() {
        checkDiscoveredProperties(
            Generics<*>::methodWithRawType,
            eq(2),
            { _, map, _ -> map == null },
            { _, map, r -> map != null && r == "value" },
        )
    }

    @Test
    fun testMethodWithArrayTypeBoundary() {
        checkDiscoveredPropertiesWithExceptions(
            Generics<*>::methodWithArrayTypeBoundary,
            eq(1),
            { _, r -> r.exceptionOrNull() is java.lang.NullPointerException },
        )
    }
}