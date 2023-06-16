package org.usvm.samples.algorithms

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class BinarySearchTest : JavaMethodTestRunner() {
    @Test
    fun testLeftBinarySearch() {
        checkWithExceptionExecutionMatches(
            BinarySearch::leftBinSearch,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a.size >= 2 && a[0] > a[1] && r.isException<IllegalArgumentException>() },
            { _, a, _, r -> a.isEmpty() && r.getOrNull() == -1 },
            { _, a, key, r -> a.isNotEmpty() && key >= a[(a.size - 1) / 2] && key !in a && r.getOrNull() == -1 },
            { _, a, key, r -> a.isNotEmpty() && key in a && r.getOrNull() == a.indexOfFirst { it == key } + 1 }
        )
    }

    @Test
    fun testRightBinarySearch() {
        checkWithExceptionExecutionMatches(
            BinarySearch::rightBinSearch,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a.isEmpty() && r.getOrNull() == -1 },
            { _, a, _, r -> a.size >= 2 && a[0] > a[1] && r.isException<IllegalArgumentException>() },
            { _, a, key, r -> a.isNotEmpty() && key !in a && r.getOrNull() == -1 },
            { _, a, key, r -> a.isNotEmpty() && key in a && r.getOrNull() == a.indexOfLast { it == key } + 1 }
        )
    }

    @Test
    fun testDefaultBinarySearch() {
        checkWithExceptionExecutionMatches(
            BinarySearch::defaultBinarySearch,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a.isEmpty() && r.getOrNull() == -1 },
            { _, a, _, r -> a.size >= 2 && a[0] > a[1] && r.isException<IllegalArgumentException>() },
            { _, a, key, r -> a.isNotEmpty() && key < a.first() && r.getOrNull() == a.binarySearch(key) },
            { _, a, key, r -> a.isNotEmpty() && key == a.first() && r.getOrNull() == a.binarySearch(key) },
        )
    }
}