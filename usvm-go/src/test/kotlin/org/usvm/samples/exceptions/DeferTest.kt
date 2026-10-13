package org.usvm.samples.exceptions

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class DeferTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "panicking")
    fun panicking() {
        checkDiscoveredProperties(
            method = "panicking",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isPanic && r.panicValue == "oh no" },
        )
    }

    @Test
    @GoSample(method = "panicRecoverSimple")
    fun panicRecoverSimple() {
        checkDiscoveredProperties(
            method = "panicRecoverSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.long == 0L },
        )
    }

    @Test
    @GoSample(method = "panicRecoverResultSimple")
    fun panicRecoverResultSimple() {
        checkDiscoveredProperties(
            method = "panicRecoverResultSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.long == 8L },
        )
    }

    @Test
    @GoSample(method = "verySimple")
    fun verySimple() {
        checkDiscoveredProperties(
            method = "verySimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, r: GoResult -> a.toLong() == 3L && r.long == 3L },
            { a: Number, r: GoResult -> a.toLong() != 3L && r.long == 5L },
        )
    }

    @Test
    @GoSample(method = "simple")
    fun simple() {
        checkDiscoveredProperties(
            method = "simple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, r: GoResult -> r.long == nativeInt(a.toLong() + 5) },
        )
    }

    @Test
    @GoSample(method = "panicRecoverComplex")
    fun panicRecoverComplex() {
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkDiscoveredProperties(
            method = "panicRecoverComplex",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { _: Number, r: GoResult -> r.long == -227L },
        )
    }
}
