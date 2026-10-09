package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.longValues
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SliceAllocationTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "sliceAlloc")
    fun sliceAlloc() {
        checkDiscoveredProperties(
            method = "sliceAlloc",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { size: Number, r: GoResult -> size.toLong() < 0 && r.isPanic },
            { size: Number, r: GoResult ->
                size.toLong() >= 0 && r.list?.longValues() == List(size.toInt()) { index ->
                    if (size.toLong() in 5..10 && index == 3) 111L else 0L
                }
            },
        )
    }

    @Test
    @GoSample(method = "sliceCustomAppend")
    fun sliceCustomAppend() {
        checkDiscoveredProperties(
            method = "sliceCustomAppend",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.list?.longValues() == listOf(1L, 2L, 3L, 4L, 5L, 123L) },
        )
    }

    @Test
    @GoSample(method = "sliceAppend")
    fun sliceAppend() {
        checkDiscoveredProperties(
            method = "sliceAppend",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> r.list?.longValues() == a.longValues() + listOf(3L, 1L, 2L, 4L) },
        )
    }

    @Test
    @GoSample(method = "sliceAppendSimple")
    fun sliceAppendSimple() {
        checkDiscoveredProperties(
            method = "sliceAppendSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().isEmpty() && r.list?.longValues() == listOf(1L, 2L) },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().isNotEmpty() &&
                    r.list?.longValues() == a.longValues() + listOf(5L, 6L)
            },
        )
    }

    @Test
    @GoSample(method = "sliceAppendTwo")
    fun sliceAppendTwo() {
        checkDiscoveredProperties(
            method = "sliceAppendTwo",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, b: GoSlice, r: GoResult ->
                (a.orEmpty().size !in 5..10 || b.orEmpty().size !in 5..10) &&
                    r.isSuccess &&
                    r.value == null
            },
            { a: GoSlice, b: GoSlice, r: GoResult ->
                a.orEmpty().size in 5..10 &&
                    b.orEmpty().size in 5..10 &&
                    r.list?.longValues() == a.longValues() + b.longValues() + 4L
            },
        )
    }
}
