package org.usvm.samples.lambda

import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.test.Test

class InvokeDynamicExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleUnaryFunction() {
        checkDiscoveredProperties(
            InvokeDynamicExamples::testUnaryFunction,
            eq(1),
            { data, res -> res == data + 2 + 17 }
        )
    }

    @Test
    fun testMethodRefUnaryFunction() {
        checkDiscoveredProperties(
            InvokeDynamicExamples::testMethodRefUnaryFunction,
            eq(1),
            { data, res -> res == data + 2 + 17 }
        )
    }

    @Test
    fun testSimpleCurryingFunction() {
        checkDiscoveredProperties(
            InvokeDynamicExamples::testCurryingFunction,
            eq(1),
            { data, res -> res == data + 2 + 42 + 17 }
        )
    }

    @Test
    fun testSimpleSamFunction() {
        checkDiscoveredProperties(
            InvokeDynamicExamples::testSamFunction,
            eq(1),
            { data, res -> res == data + 2 + 17 }
        )
    }

    @Test
    fun testSamWithDefaultFunction() {
        checkDiscoveredProperties(
            InvokeDynamicExamples::testSamWithDefaultFunction,
            eq(1),
            { data, res -> res == data + 2 + 31 + 17 }
        )
    }

    @Test
    fun testComplexInvokeDynamic() {
        checkDiscoveredProperties(
            InvokeDynamicExamples::testComplexInvokeDynamic,
            ignoreNumberOfAnalysisResults
        )
    }
}
