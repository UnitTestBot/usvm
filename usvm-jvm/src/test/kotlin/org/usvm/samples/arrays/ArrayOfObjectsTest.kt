package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class ArrayOfObjectsTest : JavaMethodTestRunner() {
    @Test
    fun testDefaultValues() {
        checkDiscoveredProperties(
            ArrayOfObjects::defaultValues,
            eq(1),
            { _, r -> r != null && r.single() == null },
        )
    }

    @Test
    @Disabled("Expected exactly 2 executions, but 3 found. Same exception discovered multiple times")
    fun testCreateArray() {
        checkDiscoveredProperties(
            ArrayOfObjects::createArray,
            eq(2),
            { _, _, _, length, _ -> length < 3 },
            { _, x, y, length, r ->
                require(r != null)

                val sizeConstraint = length >= 3 && r.size == length
                val contentConstraint = r.mapIndexed { i, elem -> elem.x == x + i && elem.y == y + i }.all { it }

                sizeConstraint && contentConstraint
            }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testCopyArray() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayOfObjects::copyArray,
            ge(4),
            { _, a, r -> a == null && r.isException<NullPointerException>() },
            { _, a, r -> a.size < 3 && r.isException<IllegalArgumentException>() },
            { _, a, r -> a.size >= 3 && null in a && r.isException<NullPointerException>() },
            { _, a, r -> a.size >= 3 && r.getOrThrow().all { it.x == -1 && it.y == 1 } },
        )
    }

    // TODO unsupported matchers
//    @Test
//    fun testCopyArrayMutation() {
//        checkParamsMutations(
//            ArrayOfObjects::copyArray,
//            ignoreNumberOfAnalysisResults,
//            { _, arrayAfter -> arrayAfter.all { it.x == -1 && it.y == 1 } }
//        )
//    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testArrayWithSucc() {
        checkDiscoveredProperties(
            ArrayOfObjects::arrayWithSucc,
            ignoreNumberOfAnalysisResults,
            { _, length, _ -> length < 0 },
            { _, length, r -> length < 2 && r != null && r.size == length && r.all { it == null } },
            { _, length, r ->
                require(r != null)

                val sizeConstraint = length >= 2 && r.size == length
                val zeroElementConstraint = r[0] is ObjectWithPrimitivesClass && r[0].x == 2 && r[0].y == 4
                val firstElementConstraint = r[1] is ObjectWithPrimitivesClassSucc && r[1].x == 3

                sizeConstraint && zeroElementConstraint && firstElementConstraint
            },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2].")
    fun testObjectArray() {
        checkDiscoveredProperties(
            ArrayOfObjects::objectArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, _ -> a == null },
            { _, a, _, r -> a != null && a.size != 2 && r == -1 },
            { _, a, o, _ -> a != null && a.size == 2 && o == null },
            { _, a, p, r -> a != null && a.size == 2 && p != null && p.x + 5 > 20 && r == 1 },
            { _, a, o, r -> a != null && a.size == 2 && o != null && o.x + 5 <= 20 && r == 0 },
        )
    }

    @Test
    @Disabled("An operation is not implemented.")
    fun testArrayOfArrays() {
        checkDiscoveredProperties(
            ArrayOfObjects::arrayOfArrays,
            between(4..5), // might be two ClassCastExceptions
            { _, a, _ -> a.any { it == null } },
            { _, a, _ -> a.any { it != null && it !is IntArray } },
            { _, a, r -> (a.all { it != null && it is IntArray && it.isEmpty() } || a.isEmpty()) && r == 0 },
            { _, a, r -> a.all { it is IntArray } && r == a.sumOf { (it as IntArray).sum() } },
        )
    }
}