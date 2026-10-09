package org.usvm.samples.controlflow

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class LoopsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "loopSimple")
    fun loopSimple() {
        checkDiscoveredProperties(
            method = "loopSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult -> r.long == (maxOf(i.toLong(), 10)) },
        )
    }

    @Test
    @GoSample(method = "loopIf")
    fun loopIf() {
        checkDiscoveredProperties(
            method = "loopIf",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult ->
                val input = i.toLong()
                val expected = when {
                    input >= 10 -> input
                    input > 5 && input % 2 == 0L -> 10L
                    else -> 11L
                }
                r.long == expected
            },
        )
    }

    @Test
    @GoSample(method = "loopSum")
    fun loopSum() {
        checkDiscoveredProperties(
            method = "loopSum",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { n: Number, r: GoResult ->
                r.long == if (n.toLong() <= 0) 0L else nativeInt(n.toLong() * (n.toLong() + 1) / 2)
            },
        )
    }
}
