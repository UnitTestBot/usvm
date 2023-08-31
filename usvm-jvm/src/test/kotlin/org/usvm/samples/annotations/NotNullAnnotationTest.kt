package org.usvm.samples.annotations

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class NotNullAnnotationTest : JavaMethodTestRunner() {
    @Test
    fun testDoesNotThrowNPE() {
        checkDiscoveredProperties(
            NotNullAnnotation::doesNotThrowNPE,
            /*eq(1)*/ignoreNumberOfAnalysisResults,
            { _, value, r -> value == r },
            /*invariants = arrayOf(
                { _, value, _ -> value != null }
            )*/
            // TODO support NotNull annotations
        )
    }

    @Test
    fun testThrowsNPE() {
        checkDiscoveredProperties(
            NotNullAnnotation::throwsNPE,
            /*eq(2)*/ignoreNumberOfAnalysisResults, // TODO support NotNull annotations,
            { _, value, _ -> value == null },
            { _, value, r -> value == r },
        )
    }

    @Test
    fun testSeveralParameters() {
        checkDiscoveredProperties(
            NotNullAnnotation::severalParameters,
            /*eq(2)*/ignoreNumberOfAnalysisResults,
            { _, _, second, _, _ -> second == null },
            { _, first, second, third, result -> first + second + third == result },
            /*invariants = arrayOf(
                { _, first, _, third, _ -> first != null && third != null }
            )*/
            // TODO support NotNull annotations
        )
    }

    @Test
    fun testUseNotNullableValue() {
        checkDiscoveredProperties(
            NotNullAnnotation::useNotNullableValue,
            /*eq(1)*/ignoreNumberOfAnalysisResults,
            { _, value, r -> value == r },
            /*invariants = arrayOf(
                { _, value, _ -> value != null }
            )*/
            // TODO support NotNull annotations
        )
    }

    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testNotNullableVariable() {
        checkDiscoveredProperties(
            NotNullAnnotation::notNullableVariable,
            /*eq(2)*/ignoreNumberOfAnalysisResults,
            { _, first, second, third, r -> first + second + third == r },
            { _, _, second, _, _ -> second == null },
            /*invariants = arrayOf(
                { _, first, _, third, _ -> first != null && third != null },
            )*/
            // TODO support NotNull annotations
        )
    }

    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testNotNullField() {
        checkDiscoveredProperties(
            NotNullAnnotation::notNullField,
            /*eq(1)*/ignoreNumberOfAnalysisResults, // TODO support NotNull annotations,
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
            /*eq(1)*/ignoreNumberOfAnalysisResults, // TODO support NotNull annotations,
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
