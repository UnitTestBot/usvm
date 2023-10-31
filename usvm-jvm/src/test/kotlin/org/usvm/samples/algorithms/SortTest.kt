package org.usvm.samples.algorithms

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class SortTest : JavaMethodTestRunner() {
    @Test
    @Disabled("TODO discover why there are no meaningful non-exceptional executions")
    fun testQuickSort() {
        checkDiscoveredPropertiesWithExceptions(
            Sort::quickSort,
            ignoreNumberOfAnalysisResults,
            { _, _, begin, end, r -> end > begin && r.isSuccess } // check that we have found at least one meaningful non-exceptional execution
            // TODO coverage or properties
        )
    }

    @Test
    fun testSwap() {
        checkDiscoveredPropertiesWithExceptions(
            Sort::swap,
            ignoreNumberOfAnalysisResults,
            { _, a, _, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, i, _, r -> a != null && (i < 0 || i >= a.size) && r.isException<IndexOutOfBoundsException>() },
            { _, a, i, j, r -> a != null && i in a.indices && (j < 0 || j >= a.size) && r.isException<IndexOutOfBoundsException>() },
            { _, a, i, j, _ -> a != null && i in a.indices && j in a.indices }
        )
    }

    @Test
    fun testArrayCopy() {
        checkDiscoveredProperties(
            Sort::arrayCopy,
            ignoreNumberOfAnalysisResults,
            { _, r -> r contentEquals intArrayOf(1, 2, 3) }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [2, 3]")
    fun testMergeSort() {
        checkDiscoveredProperties(
            Sort::mergeSort,
            ignoreNumberOfAnalysisResults,
            { _, a, r -> a == null && r == null },
            { _, a, r -> a != null && r != null && a.size < 2 },
            { _, a, r ->
                require(a is IntArray && r is IntArray)

                val sortedConstraint = a.size >= 2 && a.sorted() == r.toList()

                val maxInLeftHalf = a.slice(0 until a.size / 2).maxOrNull()!!
                val maxInRightHalf = a.slice(a.size / 2 until a.size).maxOrNull()!!

                sortedConstraint && maxInLeftHalf >= maxInRightHalf
            },
            { _, a, r ->
                require(a is IntArray && r is IntArray)

                val sortedConstraint = a.size >= 2 && a.sorted() == r.toList()

                val maxInLeftHalf = a.slice(0 until a.size / 2).maxOrNull()!!
                val maxInRightHalf = a.slice(a.size / 2 until a.size).maxOrNull()!!

                sortedConstraint && maxInLeftHalf < maxInRightHalf
            },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 3, 4, 5]. Tune path selectors")
    fun testMerge() {
        checkDiscoveredPropertiesWithExceptions(
            Sort::merge,
            ignoreNumberOfAnalysisResults,
            { _, lhs, _, r -> lhs == null && r.isException<NullPointerException>() },
            { _, lhs, rhs, r -> lhs != null && lhs.isEmpty() && r.getOrNull() contentEquals rhs },
            { _, lhs, rhs, _ -> lhs != null && lhs.isNotEmpty() && rhs == null },
            { _, lhs, rhs, r ->
                val lhsCondition = lhs != null && lhs.isNotEmpty()
                val rhsCondition = rhs != null && rhs.isEmpty()
                val connection = r.getOrNull() contentEquals lhs

                lhsCondition && rhsCondition && connection
            },
            { _, lhs, rhs, r ->
                val lhsCondition = lhs != null && lhs.isNotEmpty()
                val rhsCondition = rhs != null && rhs.isNotEmpty()
                val connection = lhs.last() < rhs.last() && r.getOrNull()?.toList() == (lhs + rhs).sorted()

                lhsCondition && rhsCondition && connection
            },
            { _, lhs, rhs, r ->
                val lhsCondition = lhs != null && lhs.isNotEmpty()
                val rhsCondition = rhs != null && rhs.isNotEmpty()
                val connection = lhs.last() >= rhs.last() && r.getOrNull()?.toList() == (lhs + rhs).sorted()

                lhsCondition && rhsCondition && connection
            }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]. Tune path selectors")
    fun testDefaultSort() {
        checkDiscoveredPropertiesWithExceptions(
            Sort::defaultSort,
            ignoreNumberOfAnalysisResults,
            { _, a, r -> a == null && r.isException<NullPointerException>() },
            { _, a, r -> a != null && a.size < 4 && r.isException<IllegalArgumentException>() },
            { _, a, r ->
                val resultArray = intArrayOf(-100, 0, 100, 200)
                a != null && r.getOrNull()!!.size >= 4 && r.getOrNull() contentEquals resultArray
            }
        )
    }
}