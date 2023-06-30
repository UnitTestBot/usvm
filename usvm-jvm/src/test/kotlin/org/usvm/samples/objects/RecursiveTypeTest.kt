package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class RecursiveTypeTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testNextValue() {
        checkDiscoveredProperties(
            RecursiveType::nextValue,
            ignoreNumberOfAnalysisResults,
            { _, _, value, _ -> value == 0 },
            { _, node, _, _ -> node == null },
            { _, node, _, _ -> node != null && node.next == null },
            { _, node, value, r -> node?.next != null && node.next.value != value && r == null },
            { _, node, value, r -> node?.next != null && node.next.value == value && r != null && r.value == value },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2]. ")
    fun testWriteObjectFieldTest() {
        checkDiscoveredProperties(
            RecursiveType::writeObjectField,
            ignoreNumberOfAnalysisResults,
            { _, node, _ -> node == null },
            { _, node, r ->
                node != null && node.next == null && r?.next != null && r.next.value == RecursiveTypeClass().value + 1
            },
            { _, node, r -> node?.next != null && r?.next != null && node.next.value + 1 == r.next.value },
        )
    }
}
