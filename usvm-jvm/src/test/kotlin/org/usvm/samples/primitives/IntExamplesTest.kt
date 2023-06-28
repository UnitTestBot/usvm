package org.usvm.samples.primitives

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("ConvertTwoComparisonsToRangeCheck")
internal class IntExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: String constants")
    fun testIsInteger() {
        val method = IntExamples::isInteger
        checkDiscoveredProperties(
            method,
            eq(2),
            { value, r -> runCatching { Integer.valueOf(value) }.isSuccess && r == true },
            { value, r -> runCatching { Integer.valueOf(value) }.isFailure && r == false },
        )
    }

    @Test
    fun testMax() {
        checkDiscoveredProperties(
            IntExamples::max,
            eq(2),
            { _, x, y, r -> x > y && r == x },
            { _, x, y, r -> x <= y && r == y }
        )
    }

    @Test
    fun testPreferableLt() {
        checkDiscoveredProperties(
            IntExamples::preferableLt,
            eq(2),
            { _, x, r -> x <= 41 && r == x },
            { _, x, r -> x >= 42 && r == x }
        )
    }

    @Test
    fun testPreferableLe() {
        checkDiscoveredProperties(
            IntExamples::preferableLe,
            eq(2),
            { _, x, r -> x <= 42 && r == x },
            { _, x, r -> x >= 43 && r == x }
        )
    }

    @Test
    fun testPreferableGe() {
        checkDiscoveredProperties(
            IntExamples::preferableGe,
            eq(2),
            { _, x, r -> x >= 42 && r == x },
            { _, x, r -> x <= 41 && r == x }
        )
    }

    @Test
    fun testPreferableGt() {
        checkDiscoveredProperties(
            IntExamples::preferableGt,
            eq(2),
            { _, x, r -> x >= 43 && r == x },
            { _, x, r -> x <= 42 && r == x }
        )
    }


    @Test
    fun testCompare() {
        checkDiscoveredProperties(
            IntExamples::complexCompare,
            eq(6),
            { _, a, b, r -> a < b && b < 11 && r == 0 },
            { _, a, b, r -> a < b && b > 11 && r == 1 },
            { _, a, b, r -> a == b && b == 11 && r == 3 },
            { _, a, b, r -> a == b && b != 11 && r == 6 },
            /**
             *  This property is not covered since according to the bytecode
             *  all instructions are covered
             */
            { _, a, b, r -> a < b && b == 11 && r == 6 },
            { _, a, b, r -> a > b && r == 6 }
        )
    }

    @Test
    fun testComplexCondition() {
        checkDiscoveredProperties(
            IntExamples::complexCondition,
            eq(3),
            { _, _, b, r -> b + 10 >= b + 22 && r == 0 }, // negative overflow, result = 1
            /**
             *  This property is not covered since according to the bytecode
             *  all instructions are covered
             */
            { _, a, b, r -> b + 10 < b + 22 && b + 22 >= a + b + 10 && r == 0 },
            { _, a, b, r -> b + 10 < b + 22 && b + 22 < a + b + 10 && r == 1 } // overflow involved
        )
    }

    @Test
    fun testOrderCheck() {
        checkDiscoveredProperties(
            IntExamples::orderCheck,
            eq(3),
            { _, first, second, _, r -> first >= second && r == false },
            /**
             *  This property is not covered since according to the bytecode
             *  all instructions are covered
             */
            { _, first, second, third, r -> first < second && second >= third && r == false },
            { _, first, second, third, r -> first < second && second < third && r == true }
        )
    }

    @Test
    fun testOrderCheckWithMethods() {
        checkDiscoveredProperties(
            IntExamples::orderCheckWithMethods,
            eq(3),
            { _, first, second, _, r -> first >= second && r == false },
            /**
             *  This property is not covered since according to the bytecode
             *  all instructions are covered
             */
            { _, first, second, third, r -> first < second && second >= third && r == false },
            { _, first, second, third, r -> first < second && second < third && r == true }
        )
    }
}