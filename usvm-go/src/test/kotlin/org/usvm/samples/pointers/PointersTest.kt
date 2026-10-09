package org.usvm.samples.pointers

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class PointersTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "pointerSimple")
    fun pointerSimple() {
        checkDiscoveredProperties(
            method = "pointerSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult -> r.long == nativeInt(i.toLong() + 9) },
        )
    }

    @Test
    @GoSample(method = "pointerOther")
    fun pointerOther() {
        checkDiscoveredProperties(
            method = "pointerOther",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult -> r.long == nativeInt(i.toLong() + 6) },
        )
    }

    @Test
    @GoSample(method = "pointerAnother")
    fun pointerAnother() {
        checkDiscoveredProperties(
            method = "pointerAnother",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { _: Number, r: GoResult -> r.long == 3L },
        )
    }

    @Test
    @GoSample(method = "pointerChangeType")
    fun pointerChangeType() {
        checkDiscoveredProperties(
            method = "pointerChangeType",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { pointer: GoPointer?, r: GoResult -> r.isSuccess && r.value == pointer },
        )
    }
}
