package org.usvm.samples.controlflow

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ConditionsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "max2")
    fun max2() {
        checkDiscoveredProperties(
            method = "max2",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, r: GoResult -> a.toLong() > b.toLong() && r.long == a.toLong() },
            { a: Number, b: Number, r: GoResult -> a.toLong() <= b.toLong() && r.long == b.toLong() },
        )
    }

    @Test
    @GoSample(method = "max2Anon")
    fun max2Anon() {
        checkDiscoveredProperties(
            method = "max2Anon",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, r: GoResult -> a.toLong() > b.toLong() && r.long == a.toLong() },
            { a: Number, b: Number, r: GoResult -> a.toLong() <= b.toLong() && r.long == b.toLong() },
        )
    }

    @Test
    @GoSample(method = "max2Closure")
    fun max2Closure() {
        checkDiscoveredProperties(
            method = "max2Closure",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, r: GoResult -> a.toLong() > b.toLong() && r.long == a.toLong() },
            { a: Number, b: Number, r: GoResult -> a.toLong() <= b.toLong() && r.long == b.toLong() },
        )
    }

    @Test
    @GoSample(method = "MinPublic")
    fun minPublic() {
        checkDiscoveredProperties(
            method = "MinPublic",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, r: GoResult -> r.long == minOf(a.toLong(), b.toLong()) },
        )
    }

    @Test
    @GoSample(method = "max3")
    fun max3() {
        checkDiscoveredProperties(
            method = "max3",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, c: Number, r: GoResult ->
                r.long == maxOf(a.toLong(), b.toLong(), c.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "max3Call")
    fun max3Call() {
        checkDiscoveredProperties(
            method = "max3Call",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, c: Number, r: GoResult ->
                r.long == maxOf(a.toLong(), b.toLong(), c.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "max4Call")
    fun max4Call() {
        checkDiscoveredProperties(
            method = "max4Call",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, b: Number, c: Number, d: Number, r: GoResult ->
                r.long == maxOf(a.toLong(), b.toLong(), c.toLong(), d.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "inc")
    fun inc() {
        checkDiscoveredProperties(
            method = "inc",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: Number, increment: Boolean, r: GoResult -> increment && r.long == nativeInt(a.toLong() + 1) },
            { a: Number, increment: Boolean, r: GoResult -> !increment && r.long == a.toLong() },
        )
    }
}
