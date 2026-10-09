package org.usvm.samples.calls

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoFunctionReference
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longValues
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class CallsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "beforeAndAfter")
    fun beforeAndAfter() {
        checkDiscoveredProperties(
            method = "beforeAndAfter",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, r: GoResult ->
                r.list?.longValues() == listOf(nativeInt(a.toLong() - 1), nativeInt(a.toLong() + 1))
            },
        )
    }

    @Test
    @GoSample(method = "sumBeforeAndAfter")
    fun sumBeforeAndAfter() {
        checkDiscoveredProperties(
            method = "sumBeforeAndAfter",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, r: GoResult -> r.long == nativeInt(a.toLong() * 2) },
        )
    }

    /** Function inputs are mocked; this checks the callback mock contract. */
    @Test
    @GoSample(method = "call")
    fun call() {
        checkDiscoveredProperties(
            method = "call",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { f: GoFunctionReference?, r: GoResult -> f != null && r.isSuccess && r.value is Number },
        )
    }
}
