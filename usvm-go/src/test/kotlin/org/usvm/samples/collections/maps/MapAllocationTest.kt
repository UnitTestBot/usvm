package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longEntry
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MapAllocationTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "mapAlloc")
    fun mapAlloc() {
        checkDiscoveredProperties(
            method = "mapAlloc",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { _: Number, r: GoResult ->
                r.map?.size == 3 &&
                    r.map.longEntry(key = 3) == 111L &&
                    r.map.longEntry(key = -226) == 13L &&
                    r.map.longEntry(key = 0) == -1L
            },
        )
    }

    @Test
    @GoSample(method = "mapCustomAlloc")
    fun mapCustomAlloc() {
        checkDiscoveredProperties(
            method = "mapCustomAlloc",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.map?.size == 1 && r.map.longEntry(key = 2) == 3L },
        )
    }
}
