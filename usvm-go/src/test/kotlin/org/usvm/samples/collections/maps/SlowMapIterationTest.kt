package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.GoExamplesReplay
import org.usvm.samples.GoMap
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longEntry
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
                    val maximum = maxOf(0L, values.max())
                    val minimum = minOf(0L, values.min())
                    val atZero = m.longEntry(key = 0L)
                    if (maximum == minimum) {
                        minimum
                    } else {
                        val upper = if (maximum > 0L) maximum else atZero
                        val lower = if (minimum < 0L) minimum else atZero
                        nativeInt(upper - lower)
                    }
                }
                r.long == expected
            },
        )

        GoExamplesReplay.replay(method = "mapLoopLen", executions = runner("mapLoopLen", options))
    }
}
