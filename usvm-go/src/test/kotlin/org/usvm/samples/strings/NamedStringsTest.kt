package org.usvm.samples.strings

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class NamedStringsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "(usvm/examples.errorString).Error")
    fun errorStringError() {
        checkDiscoveredProperties(
            method = "(usvm/examples.errorString).Error",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { s: String, r: GoResult -> r.isSuccess && r.value == "runtime error: $s" },
        )
    }

    @Test
    @GoSample(method = "shiftErrorToString")
    fun shiftErrorToString() {
        checkDiscoveredProperties(
            method = "shiftErrorToString",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isSuccess && r.value == "runtime error: negative shift amount" },
        )
    }

    @Test
    @GoSample(method = "appendErrorStrings")
    fun appendErrorStrings() {
        checkDiscoveredProperties(
            method = "appendErrorStrings",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isSuccess && r.value == "hello, world!" },
        )
    }
}
