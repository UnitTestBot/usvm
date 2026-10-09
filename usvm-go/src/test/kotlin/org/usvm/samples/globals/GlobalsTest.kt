package org.usvm.samples.globals

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class GlobalsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "globalSimple")
    fun globalSimple() {
        checkDiscoveredProperties(
            method = "globalSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, r: GoResult -> a.toLong() == 555L && r.long == 444L },
            { a: Number, r: GoResult -> a.toLong() > 555 && r.long == nativeInt(a.toLong() - 555) },
            { a: Number, r: GoResult -> a.toLong() < 555 && r.long == nativeInt(555 - a.toLong()) },
        )
    }

    @Test
    @GoSample(method = "globalArraySimple")
    fun globalArraySimple() {
        checkDiscoveredProperties(
            method = "globalArraySimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult -> i.toLong() !in 0..255 && r.long == -1L },
            { i: Number, r: GoResult -> i.toLong() in 0..255 && r.long == 5L },
        )
    }

    @Test
    @GoSample(method = "init")
    fun init() {
        checkDiscoveredProperties(
            method = "init",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isSuccess },
        )
    }

    @Test
    @GoSample(method = "init", fixture = "regressions")
    fun regressionPackageInit() {
        val runner = object : GoMethodTestRunner(fixture = "regressions") {
            fun checkInitializer() = checkDiscoveredProperties(
                method = "init",
                analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
                { result: GoResult -> result.isSuccess },
            )
        }

        runner.checkInitializer()
    }
}
