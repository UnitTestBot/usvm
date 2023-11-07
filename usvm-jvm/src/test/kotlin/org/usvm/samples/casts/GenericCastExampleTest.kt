package org.usvm.samples.casts

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq

internal class GenericCastExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Expected exactly 5 executions, but 1 found. Support generics")
    fun testCompareTwoNumbers() {
        checkDiscoveredProperties(
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
    @Disabled("TODO process generic fields in type constraints properly")
    fun testGetGenericFieldValue() {
        checkDiscoveredProperties(
            GenericCastExample::getGenericFieldValue,
            eq(3),
            { _, g, _ -> g == null },
            { _, g, _ -> g.genericField == null },
            { _, g, r -> g?.genericField != null && r == g.genericField },
        )
    }

    @Test
    @Disabled("List is empty. Support generics")
    fun testCompareGenericField() {
        checkDiscoveredProperties(
            GenericCastExample::compareGenericField,
            between(4..5),
            { _, g, _, _ -> g == null },
            { _, g, v, _ -> g != null && v == null },
            { _, g, v, r -> v != null && v != g.comparableGenericField && r == -1 },
            { _, g, v, r -> g.comparableGenericField is Int && v != null && v == g.comparableGenericField && r == 1 },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testCreateNewGenericObject() {
        checkDiscoveredProperties(
            GenericCastExample::createNewGenericObject,
            eq(1),
            { _, r -> r == 10 },
        )
    }

    @Test
    @Disabled("List is empty. Support generics")
    fun testSumFromArrayOfGenerics() {
        checkDiscoveredProperties(
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