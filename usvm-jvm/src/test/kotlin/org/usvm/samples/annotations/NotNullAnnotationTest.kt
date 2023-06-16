package org.usvm.samples.annotations

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class NotNullAnnotationTest : JavaMethodTestRunner() {
    @Test
    fun testDoesNotThrowNPE() {
        checkExecutionMatches(
            NotNullAnnotation::doesNotThrowNPE,
            eq(1),
            { _, value, r -> value == r }
        )
    }

    @Test
    fun testThrowsNPE() {
        checkExecutionMatches(
            NotNullAnnotation::throwsNPE,
            eq(2),
            { _, value, _ -> value == null },
            { _, value, r -> value == r }
        )
    }

    @Test
    fun testSeveralParameters() {
        checkExecutionMatches(
            NotNullAnnotation::severalParameters,
            eq(2),
            { _, _, second, _, _ -> second == null },
            { _, first, second, third, result -> first + second + third == result }
        )
    }

    @Test
    fun testUseNotNullableValue() {
        checkExecutionMatches(
            NotNullAnnotation::useNotNullableValue,
            eq(1),
            { _, value, r -> value == r }
        )
    }

    @Test
    @Disabled("Annotations for local variables are not supported yet")
    fun testNotNullableVariable() {
        checkExecutionMatches(
            NotNullAnnotation::notNullableVariable,
            eq(1),
            { _, first, second, third, r -> first + second + third == r }
        )
    }

    @Test
    fun testNotNullField() {
        checkExecutionMatches(
            NotNullAnnotation::notNullField,
            eq(1),
            { _, value, result -> value.boxedInt == result }
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
        checkExecutionMatches(
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
