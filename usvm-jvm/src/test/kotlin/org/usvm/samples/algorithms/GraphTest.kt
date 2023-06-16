package org.usvm.samples.algorithms

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class GraphTest : JavaMethodTestRunner() {
    @Test
    fun testRunFindCycle() {
        checkWithExceptionExecutionMatches(
            GraphExample::runFindCycle,
            ignoreNumberOfAnalysisResults,
            { _, e, r -> e == null && r.isException<NullPointerException>() },
            { _, e, r -> e != null && e.contains(null) && r.isException<NullPointerException>() },
            { _, e, r -> e != null && e.any { it.first < 0 || it.first >= 10 } && r.isException<ArrayIndexOutOfBoundsException>() },
            { _, e, r -> e != null && e.any { it.second < 0 || it.second >= 10 } && r.isException<ArrayIndexOutOfBoundsException>() },
            { _, e, r -> e != null && e.all { it != null } && r.isSuccess }
        )
    }

    @Test
    fun testDijkstra() {
        // The graph is fixed, there should be exactly one execution path, so no matchers are necessary
        checkExecutionMatches(
            GraphExample::runDijkstra,
            eq(1)
        )
    }

    /**
     * TODO: fix Dijkstra algorithm.
     */
    @Test
    fun testRunDijkstraWithParameter() {
        checkWithExceptionExecutionMatches(
            GraphExample::runDijkstraWithParameter,
            ignoreNumberOfAnalysisResults,
            { _, g, r -> g == null && r.isException<NullPointerException>() },
            { _, g, r -> g.isEmpty() && r.isException<IndexOutOfBoundsException>() },
            { _, g, r -> g.size == 1 && r.getOrNull()?.size == 1 && r.getOrNull()?.first() == 0 },
            { _, g, r -> g.size > 1 && g[1] == null && r.isException<IndexOutOfBoundsException>() },
            { _, g, r -> g.isNotEmpty() && g.size != g.first().size && r.isException<IndexOutOfBoundsException>() },
            { _, g, r ->
                val concreteResult = GraphExample().runDijkstraWithParameter(g)
                g.isNotEmpty() && r.getOrNull().contentEquals(concreteResult)
            }
        )
    }
}