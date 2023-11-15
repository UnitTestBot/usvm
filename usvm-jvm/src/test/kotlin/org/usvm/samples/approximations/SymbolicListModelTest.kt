package org.usvm.samples.approximations

import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.test.Test

class SymbolicListModelTest : ApproximationsTestRunner() {
    init {
        options = options.copy(timeoutMs = null, stepsFromLastCovered = null, stopOnCoverage = 200)
    }

    @Test
    fun testSymbolicListModel() {
        checkDiscoveredProperties(
            ApproximationsApiExample::symbolicList,
            ignoreNumberOfAnalysisResults,
            { list, res -> res == 0 && list.size() < 10 },
            { list, res -> res == 1 && list.size() >= 10 && list[3] == 5 },
            { list, res -> res == 2 && list.size() >= 10 && list[3] != 5 && list[2] == 7 },
            { list, res -> res == 3 && list.size() >= 10 && list[3] != 5 && list[2] != 7 },
        )
    }
}
