package org.usvm.samples.strings

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class StringsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "stringAppend")
    fun stringAppend() {
        checkDiscoveredProperties(
            method = "stringAppend",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isSuccess && r.value == "hello, world" },
        )
    }

    @Test
    @GoSample(method = "stringFromByteArray")
    fun stringFromByteArray() {
        checkDiscoveredProperties(
            method = "stringFromByteArray",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isSuccess && r.value == "hello" },
        )
    }

    @Test
    @GoSample(method = "stringToByteArray")
    fun stringToByteArray() {
        checkDiscoveredProperties(
            method = "stringToByteArray",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult ->
                r.list == listOf(104.toUByte(), 101.toUByte(), 108.toUByte(), 108.toUByte(), 111.toUByte())
            },
        )
    }
}
