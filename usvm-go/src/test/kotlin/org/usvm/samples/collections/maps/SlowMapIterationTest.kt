package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.GoMap
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SlowMapIterationTest : GoMethodTestRunner() {
    @Tag(value = "manual")
    @Test
    @GoSample(method = "mapLoopLen")
    fun mapLoopLen() {
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkDiscoveredProperties(
            method = "mapLoopLen",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, r: GoResult ->
                val values = m.orEmpty().values.map { it.longValue() }
                val expected = if (values.size < 4) {
                    -1L
                } else {
                    nativeInt(maxOf(0L, values.max()) - minOf(0L, values.min()))
                }
                r.long == expected
            },
        )
    }
}
