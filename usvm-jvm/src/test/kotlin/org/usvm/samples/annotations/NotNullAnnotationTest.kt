package org.usvm.samples.annotations

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SENSELESS_COMPARISON")
internal class NotNullAnnotationTest : JavaMethodTestRunner() {
    @Test
    fun testDoesNotThrowNPE() {
        checkDiscoveredProperties(
            NotNullAnnotation::doesNotThrowNPE,
            eq(1),
            { _, value, r -> value == r },
            invariants = arrayOf(
                { _, value, _ -> value != null }
            )
        )
    }

    @Test
    fun testThrowsNPE() {
        checkDiscoveredProperties(
            NotNullAnnotation::throwsNPE,
            eq(2),
            { _, value, _ -> value == null },
            { _, value, r -> value == r },
        )
    }

    @Test
    fun testSeveralParameters() {
        checkDiscoveredProperties(
            NotNullAnnotation::severalParameters,
            eq(2),
            { _, _, second, _, _ -> second == null },
            { _, first, second, third, result -> first + second + third == result },
            invariants = arrayOf(
                { _, first, _, third, _ -> first != null && third != null }
            )
        )
    }

    @Test
    fun testUseNotNullableValue() {
        checkDiscoveredProperties(
            NotNullAnnotation::useNotNullableValue,
            eq(1),
            { _, value, r -> value == r },
            invariants = arrayOf(
                { _, value, _ -> value != null }
            )
        )
    }

    @Test
    fun testNotNullableVariable() {
        checkDiscoveredProperties(
            NotNullAnnotation::notNullableVariable,
            eq(2),
            { _, first, second, third, r -> first + second + third == r },
            { _, _, second, _, _ -> second == null },
            invariants = arrayOf(
                { _, first, _, third, _ -> first != null && third != null },
            )
        )
    }

    @Test
    fun testNotNullField() {
        checkDiscoveredProperties(
            NotNullAnnotation::notNullField,
            eq(1),
            { _, value, result -> value.boxedInt == result },
        )
    }

    // TODO unsupported static checker
//    @Test
//    fun testNotNullStaticField() {
//        checkStatics(
//            NotNullAnnotation::notNullStaticField,
//            eq(1),
//            { statics, result -> result == statics.values.single().value },
//        )
//    }

    @Test
    fun testJavaxValidationNotNull() {
        checkDiscoveredProperties(
            NotNullAnnotation::javaxValidationNotNull,
            eq(1),
            { _, value, r -> value == r }
        )
    }

//    @Test
//    fun testFindBugsNotNull() {
//        checkExecutionMatches(
//            NotNullAnnotation::findBugsNotNull,
//            eq(1),
//            { _, value, r -> value == r }
//        )
//    }
}
