package org.usvm.samples.casts

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

import org.usvm.test.util.checkers.between

import org.usvm.test.util.checkers.eq

internal class GenericCastExampleTest : JavaMethodTestRunner() {
    @Test
    fun testCompareTwoNumbers() {
        checkExecutionMatches(
            GenericCastExample::compareTwoNumbers,
            eq(5),
            { _, a, _, _ -> a == null },
            { _, _, b, _ -> b == null },
            { _, _, b, _ -> b.comparableGenericField == null },
            { _, a, b, r -> a >= b.comparableGenericField && r == 1 },
            { _, a, b, r -> a < b.comparableGenericField && r == -1 },
        )
    }

    @Test
    fun testGetGenericFieldValue() {
        checkExecutionMatches(
            GenericCastExample::getGenericFieldValue,
            eq(3),
            { _, g, _ -> g == null },
            { _, g, _ -> g.genericField == null },
            { _, g, r -> g?.genericField != null && r == g.genericField },
        )
    }

    @Test
    fun testCompareGenericField() {
        checkExecutionMatches(
            GenericCastExample::compareGenericField,
            between(4..5),
            { _, g, _, _ -> g == null },
            { _, g, v, _ -> g != null && v == null },
            { _, g, v, r -> v != null && v != g.comparableGenericField && r == -1 },
            { _, g, v, r -> g.comparableGenericField is Int && v != null && v == g.comparableGenericField && r == 1 },
        )
    }

    @Test
    fun testCreateNewGenericObject() {
        checkExecutionMatches(
            GenericCastExample::createNewGenericObject,
            eq(1),
            { _, r -> r == 10 },
        )
    }

    @Test
    fun testSumFromArrayOfGenerics() {
        checkExecutionMatches(
            GenericCastExample::sumFromArrayOfGenerics,
            eq(7),
            { _, g, _ -> g == null },
            { _, g, _ -> g.genericArray == null },
            { _, g, _ -> g.genericArray.isEmpty() },
            { _, g, _ -> g.genericArray.size == 1 },
            { _, g, _ -> g.genericArray[0] == null },
            { _, g, _ -> g.genericArray[0] != null && g.genericArray[1] == null },
            { _, g, r -> g.genericArray[0] != null && g.genericArray[1] != null && r == g.genericArray[0] + g.genericArray[1] },
        )
    }
}