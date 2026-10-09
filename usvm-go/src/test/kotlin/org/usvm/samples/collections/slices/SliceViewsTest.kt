package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.longValues
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SliceViewsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "sliceCustomSlice")
    fun sliceCustomSlice() {
        checkDiscoveredProperties(
            method = "sliceCustomSlice",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.list?.longValues() == listOf(2L, 3L) },
        )
    }

    @Test
    @GoSample(method = "sliceSliceFull")
    fun sliceSliceFull() {
        checkDiscoveredProperties(
            method = "sliceSliceFull",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size !in 5..10 && r.isSuccess && r.value == null },
            { a: GoSlice, r: GoResult -> a.orEmpty().size in 5..10 && r.list == a },
        )
    }

    @Test
    @GoSample(method = "sliceSliceFrom")
    fun sliceSliceFrom() {
        checkDiscoveredProperties(
            method = "sliceSliceFrom",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, i: Number, r: GoResult ->
                (a.orEmpty().size !in 5..10) &&
                    r.isSuccess &&
                    r.value == null
            },
            { a: GoSlice, i: Number, r: GoResult ->
                !(a.orEmpty().size !in 5..10) &&
                    (i.toLong() < 0 || i.toLong() > a.orEmpty().size) &&
                    r.isPanic
            },
            { a: GoSlice, i: Number, r: GoResult ->
                !(a.orEmpty().size !in 5..10) &&
                    !(i.toLong() < 0 || i.toLong() > a.orEmpty().size) &&
                    r.isSuccess &&
                    r.list == a.orEmpty().drop(i.toInt())
            },
        )
    }

    @Test
    @GoSample(method = "sliceSliceTo")
    fun sliceSliceTo() {
        checkDiscoveredProperties(
            method = "sliceSliceTo",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, i: Number, r: GoResult ->
                (a.orEmpty().size !in 5..10 || i.toLong() > 3) &&
                    r.isSuccess &&
                    r.value == null
            },
            { a: GoSlice, i: Number, r: GoResult ->
                !(a.orEmpty().size !in 5..10 || i.toLong() > 3) &&
                    (i.toLong() < 0 || i.toLong() > a.orEmpty().size) &&
                    r.isPanic
            },
            { a: GoSlice, i: Number, r: GoResult ->
                !(a.orEmpty().size !in 5..10 || i.toLong() > 3) &&
                    !(i.toLong() < 0 || i.toLong() > a.orEmpty().size) &&
                    r.isSuccess &&
                    r.list == a.orEmpty().take(i.toInt())
            },
        )
    }

    @Test
    @GoSample(method = "sliceSlice")
    fun sliceSlice() {
        checkDiscoveredProperties(
            method = "sliceSlice",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, i: Number, j: Number, r: GoResult ->
                (a.orEmpty().size !in 5..10 || i.toLong() == j.toLong()) &&
                    r.isSuccess &&
                    r.value == null
            },
            { a: GoSlice, i: Number, j: Number, r: GoResult ->
                a.orEmpty().size in 5..10 &&
                    i.toLong() != j.toLong() &&
                    (j.toLong() !in 0..a.orEmpty().size.toLong() || i.toLong() !in 0..j.toLong()) &&
                    r.isPanic
            },
            { a: GoSlice, i: Number, j: Number, r: GoResult ->
                a.orEmpty().size in 5..10 &&
                    i.toLong() < j.toLong() &&
                    i.toLong() >= 0 &&
                    j.toLong() <= a.orEmpty().size &&
                    r.list == a.orEmpty().subList(i.toInt(), j.toInt())
            },
        )
    }

    @Test
    @GoSample(method = "sliceToArrayPointer")
    fun sliceToArrayPointer() {
        checkDiscoveredProperties(
            method = "sliceToArrayPointer",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size < 3 && r.isPanic },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size >= 3 &&
                    (r.value as? GoPointer)?.value == listOf(a?.get(0), 1L, a?.get(2))
            },
        )
    }

    @Test
    @GoSample(method = "sliceCustomToArrayPointer")
    fun sliceCustomToArrayPointer() {
        checkDiscoveredProperties(
            method = "sliceCustomToArrayPointer",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size < 3 && r.isPanic },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size >= 3 &&
                    (r.value as? GoPointer)?.value == listOf(a?.get(0), 1L, a?.get(2))
            },
        )
    }
}
