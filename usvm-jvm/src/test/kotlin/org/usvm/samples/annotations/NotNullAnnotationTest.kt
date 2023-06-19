package org.usvm.samples.annotations

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner


internal class NotNullAnnotationTest : JavaMethodTestRunner() {
    @Test
    fun testDoesNotThrowNPE() {
        checkExecutionMatches(
            NotNullAnnotation::doesNotThrowNPE,
            { _, value, r -> value == r }
        )
    }

    @Test
    fun testThrowsNPE() {
        checkExecutionMatches(
            NotNullAnnotation::throwsNPE,
            { _, value, _ -> value == null },
            { _, value, r -> value == r }
        )
    }

    @Test
    fun testSeveralParameters() {
        checkExecutionMatches(
            NotNullAnnotation::severalParameters,
            { _, _, second, _, _ -> second == null },
            { _, first, second, third, result -> first + second + third == result }
        )
    }

    @Test
    fun testUseNotNullableValue() {
        checkExecutionMatches(
            NotNullAnnotation::useNotNullableValue,
            { _, value, r -> value == r }
        )
    }

    @Test
    @Disabled("Annotations for local variables are not supported yet")
    fun testNotNullableVariable() {
        checkExecutionMatches(
            NotNullAnnotation::notNullableVariable,
            { _, first, second, third, r -> first + second + third == r }
        )
    }

    @Test
    fun testNotNullField() {
        checkExecutionMatches(
            NotNullAnnotation::notNullField,
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
