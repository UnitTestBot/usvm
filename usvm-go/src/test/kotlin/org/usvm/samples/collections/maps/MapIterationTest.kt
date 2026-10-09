package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMap
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MapIterationTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "mapLoop")
    fun mapLoop() {
        checkDiscoveredProperties(
            method = "mapLoop",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, n: Number, r: GoResult ->
                r.long == m.orEmpty().filterKeys { it.longValue() > n.toLong() }.values.fold(0L) { maximum, v ->
                    maxOf(maximum, v.longValue())
                }
            },
        )
    }
}
