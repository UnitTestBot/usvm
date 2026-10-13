package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.longValue
import org.usvm.samples.longValues
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ArraysTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "arrayIndex")
    fun arrayIndex() {
        checkDiscoveredProperties(
            method = "arrayIndex",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { _: GoSlice, i: Number, r: GoResult -> i.toLong() !in 0..2 && r.isPanic },
            { a: GoSlice, i: Number, r: GoResult ->
                i.toLong() in 0..2 &&
                    r.long == a?.get(i.toInt()).longValue()
            },
        )
    }

    @Test
    @GoSample(method = "arrayIndexMake")
    fun arrayIndexMake() {
        checkDiscoveredProperties(
            method = "arrayIndexMake",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { i: Number, r: GoResult -> i.toLong() !in 0..2 && r.isPanic },
            { i: Number, r: GoResult -> i.toLong() in 0..2 && r.long == i.toLong() + 1 },
        )
    }

    @Test
    @GoSample(method = "arraySlice")
    fun arraySlice() {
        checkDiscoveredProperties(
            method = "arraySlice",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.list?.longValues() == listOf(2L) },
        )
    }
}
