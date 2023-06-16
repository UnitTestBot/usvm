package org.usvm.samples.algorithms

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class SortTest : JavaMethodTestRunner() {
    @Test
    fun testQuickSort() {
        checkExecutionMatches(
            Sort::quickSort,
            ignoreNumberOfAnalysisResults,
            // TODO coverage or properties
        )
    }

    @Test
    fun testSwap() {
        checkWithExceptionExecutionMatches(
            Sort::swap,
            ge(4),
            { _, a, _, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, i, _, r -> a != null && (i < 0 || i >= a.size) && r.isException<IndexOutOfBoundsException>() },
            { _, a, i, j, r -> a != null && i in a.indices && (j < 0 || j >= a.size) && r.isException<IndexOutOfBoundsException>() },
            { _, a, i, j, _ -> a != null && i in a.indices && j in a.indices }
        )
    }

    @Test
    fun testArrayCopy() {
        checkExecutionMatches(
            Sort::arrayCopy,
            eq(1),
            { _, r -> r contentEquals intArrayOf(1, 2, 3) }
        )
    }

    @Test
    fun testMergeSort() {
        checkExecutionMatches(
            Sort::mergeSort,
            eq(4),
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
    fun testMerge() {
        checkWithExceptionExecutionMatches(
            Sort::merge,
            eq(6),
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
    fun testDefaultSort() {
        checkWithExceptionExecutionMatches(
            Sort::defaultSort,
            eq(3),
            { _, a, r -> a == null && r.isException<NullPointerException>() },
            { _, a, r -> a != null && a.size < 4 && r.isException<IllegalArgumentException>() },
            { _, a, r ->
                val resultArray = intArrayOf(-100, 0, 100, 200)
                a != null && r.getOrNull()!!.size >= 4 && r.getOrNull() contentEquals resultArray
            }
        )
    }
}