package org.usvm.samples.invokes

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class InvokeExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Expected exactly 3 executions, but 5 found. Same exception discovered multiple times")
    fun testSimpleFormula() {
        checkDiscoveredProperties(
            InvokeExample::simpleFormula,
            eq(3),
            { _, fst, _, _ -> fst < 100 },
            { _, _, snd, _ -> snd < 100 },
            { _, fst, snd, r -> fst >= 100 && snd >= 100 && r == (fst + 5) * (snd / 2) },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2]. ")
    fun testChangeObjectValueByMethod() {
        checkDiscoveredProperties(
            InvokeExample::changeObjectValueByMethod,
            ignoreNumberOfAnalysisResults,
            { _, o, _ -> o == null },
            { _, o, r -> o != null && r?.value == 4 },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2]. ")
    fun testParticularValue() {
        checkDiscoveredProperties(
            InvokeExample::particularValue,
            ignoreNumberOfAnalysisResults,
            { _, o, _ -> o == null },
            { _, o, _ -> o != null && o.value < 0 },
            { _, o, r -> o != null && o.value >= 0 && r?.value == 12 },
        )
    }

    @Test
    fun testCreateObjectFromValue() {
        checkDiscoveredProperties(
            InvokeExample::createObjectFromValue,
            eq(2),
            { _, value, r -> value == 0 && r?.value == 1 },
            { _, value, r -> value != 0 && r?.value == value }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testGetNullOrValue() {
        checkDiscoveredProperties(
            InvokeExample::getNullOrValue,
            ignoreNumberOfAnalysisResults,
            { _, o, _ -> o == null },
            { _, o, r -> o != null && o.value < 100 && r == null },
            { _, o, r -> o != null && o.value >= 100 && r?.value == 5 },
        )
    }


    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testConstraintsFromOutside() {
        checkDiscoveredProperties(
            InvokeExample::constraintsFromOutside,
            eq(3),
            { _, value, r -> value >= 0 && r == value },
            { _, value, r -> value == Int.MIN_VALUE && r == 0 },
            { _, value, r -> value < 0 && value != Int.MIN_VALUE && r == -value },
        )
    }


    @Test
//    @Disabled("Expected exactly 3 executions, but 2 found")
    fun testConstraintsFromInside() {
        checkDiscoveredProperties(
            InvokeExample::constraintsFromInside,
            eq(3),
            { _, value, r -> value >= 0 && r == 1 },
            { _, value, r -> value == Int.MIN_VALUE && r == 1 },
            { _, value, r -> value < 0 && value != Int.MIN_VALUE && r == 1 },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testAlwaysNPE() {
        checkDiscoveredPropertiesWithExceptions(
            InvokeExample::alwaysNPE,
            ignoreNumberOfAnalysisResults,
            { _, o, r -> o == null && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o.value == 0 && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o.value < 0 && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o.value > 0 && r.isException<NullPointerException>() },
            invariants = arrayOf(
                { _, _, r -> !r.isSuccess }
            )
        )
    }

    @Test
    @Disabled("Expected exactly 3 executions, but 5 found. Same exception discovered multiple times")
    fun testExceptionInNestedMethod() {
        checkDiscoveredPropertiesWithExceptions(
            InvokeExample::exceptionInNestedMethod,
            eq(3),
            { _, o, _, r -> o == null && r.isException<NullPointerException>() },
            { _, o, value, r -> o != null && value < 0 && r.isException<IllegalArgumentException>() },
            { _, o, value, r -> o != null && value >= 0 && value == (r.getOrNull() as InvokeClass).value },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testFewNestedExceptions() {
        checkDiscoveredPropertiesWithExceptions(
            InvokeExample::fewNestedException,
            ignoreNumberOfAnalysisResults,
            { _, o, _, r -> o == null && r.isException<NullPointerException>() },
            { _, o, value, r -> o != null && value < 10 && r.isException<IllegalArgumentException>() },
            { _, o, value, r -> o != null && value in 10..99 && r.isException<IllegalArgumentException>() },
            { _, o, value, r -> o != null && value in 100..9999 && r.isException<IllegalArgumentException>() },
            { _, o, value, r -> o != null && value >= 10000 && value == (r.getOrNull() as InvokeClass).value },
        )
    }

    @Test
    @Disabled("Expected exactly 4 executions, but 7 found. Same exception discovered multiple times")
    fun testDivBy() {
        checkDiscoveredPropertiesWithExceptions(
            InvokeExample::divBy,
            eq(4),
            { _, o, _, r -> o == null && r.isException<NullPointerException>() },
            { _, o, _, r -> o != null && o.value < 1000 && r.isException<IllegalArgumentException>() },
            { _, o, den, r -> o != null && o.value >= 1000 && den == 0 && r.isException<ArithmeticException>() },
            { _, o, den, r -> o != null && o.value >= 1000 && den != 0 && r.getOrNull() == o.value / den },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testUpdateValue() {
        checkDiscoveredProperties(
            InvokeExample::updateValue,
            ignoreNumberOfAnalysisResults,
            { _, o, _, _ -> o == null },
            { _, o, _, r -> o != null && o.value > 0 && r != null && r.value > 0 },
            { _, o, value, r -> o != null && o.value <= 0 && value > 0 && r?.value == value },
            { _, o, value, _ -> o != null && o.value <= 0 && value <= 0 },
        )
    }

    @Test
    fun testNullAsParameter() {
        checkDiscoveredPropertiesWithExceptions(
            InvokeExample::nullAsParameter,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { _, _, r -> !r.isSuccess }
            )
        )
    }

    @Test
    @Disabled("Expected exactly 3 executions, but 4 found. Same exception discovered multiple times")
    fun testChangeArrayWithAssignFromMethod() {
        checkDiscoveredProperties(
            InvokeExample::changeArrayWithAssignFromMethod,
            eq(3),
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.isEmpty() && r != null && r.isEmpty() },
            { _, a, r ->
                require(a != null && r != null)
                a.isNotEmpty() && r.size == a.size && a.map { it + 5 } == r.toList() && !a.contentEquals(r)
            }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2]. ")
    fun testChangeArrayByMethod() {
        checkDiscoveredProperties(
            InvokeExample::changeArrayByMethod,
            ignoreNumberOfAnalysisResults,
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.isNotEmpty() && r != null && r.size == a.size && a.map { it + 5 } == r.toList() }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testArrayCopyExample() {
        checkDiscoveredProperties(
            InvokeExample::arrayCopyExample,
            ignoreNumberOfAnalysisResults,
            { _, a, _ -> a == null },
            { _, a, _ -> a != null && a.size < 3 },
            { _, a, r -> a != null && a.size >= 3 && a[0] <= a[1] && r == null },
            { _, a, r -> a != null && a.size >= 3 && a[0] > a[1] && a[1] <= a[2] && r == null },
            { _, a, r -> a != null && a.size >= 3 && a[0] > a[1] && a[1] > a[2] && r.contentEquals(a) },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2]. ")
    fun testUpdateValues() {
        checkDiscoveredProperties(
            InvokeExample::updateValues,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, _ -> fst == null },
            { _, fst, snd, _ -> fst != null && snd == null },
            { _, fst, snd, r -> fst != null && snd != null && fst !== snd && r == 1 },
            { _, fst, snd, _ -> fst != null && snd != null && fst === snd },
        )
    }
}