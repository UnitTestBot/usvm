package org.usvm.samples.controlflow

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.collatz
import org.usvm.samples.innerSum
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SlowLoopsTest : GoMethodTestRunner() {
    @Tag(value = "manual")
    @Test
    @GoSample(method = "loopInner")
    fun loopInner() {
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkDiscoveredProperties(
            method = "loopInner",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, j: Number, r: GoResult -> r.long == innerSum(nativeInt(i.toLong() + j.toLong())) },
        )
    }

    @Tag(value = "manual")
    @Test
    @GoSample(method = "loopCollatz")
    fun loopCollatz() {
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkDiscoveredProperties(
            method = "loopCollatz",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult -> r.long == collatz(i.toLong()) },
        )
    }

    @Tag(value = "manual")
    @Test
    @GoSample(method = "loopInfinite")
    fun loopInfinite() {
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkNoResults(method = "loopInfinite")
    }
}
