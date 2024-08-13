package org.usvm.samples.approximations

import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.test.Test

class SymbolicCollectionModelTest : ApproximationsTestRunner() {
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

    @Test
    fun testSymbolicMapModel() {
        checkDiscoveredProperties(
            ApproximationsApiExample::symbolicMap,
            ignoreNumberOfAnalysisResults,
            { map, res -> res == 0 && map.size() < 10 },
            { map, res -> res == 1 && map.size() >= 10 && !map.containsKey("abc") },
            { map, res -> res == 2 && map.size() >= 10 && map.containsKey("abc") && map["abc"] != 5 },
            { map, res -> res == 6 && map.size() >= 10 && map["abc"] == 5 },
            // todo: fix test
            // { map, res -> res == 7 && map.size() >= 10 && map["abc"] == 5 && map["xxx"] != 17 },
            invariants = arrayOf({ _, res -> res !in 3..5 })
        )
    }
}
