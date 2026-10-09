package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.longValue
import org.usvm.samples.longValues
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SliceAlgorithmsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "sliceFirst")
    fun sliceFirst() {
        checkDiscoveredProperties(
            method = "sliceFirst",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size !in 2..20 && r.long == -1L },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size in 2..20 &&
                    a?.get(0).longValue() == 1L &&
                    a?.get(1).longValue() == 2L &&
                    r.long == 1L
            },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size in 2..20 &&
                    (a?.get(0).longValue() != 1L || a?.get(1).longValue() != 2L) &&
                    r.long == 0L
            },
        )
    }

    @Test
    @GoSample(method = "sliceSum")
    fun sliceSum() {
        checkDiscoveredProperties(
            method = "sliceSum",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size < 5 && r.long == -228L },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size >= 5 &&
                    r.long == nativeInt(a.longValues().sumOf { if (it < 0) -it else it })
            },
        )
    }

    @Test
    @GoSample(method = "sliceCompare")
    fun sliceCompare() {
        checkDiscoveredProperties(
            method = "sliceCompare",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size < 2 && r.isPanic },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size >= 2 &&
                    r.long == minOf(a?.get(0).longValue(), a?.get(1).longValue())
            },
        )
    }

    @Test
    @GoSample(method = "sliceCompareFuncVar")
    fun sliceCompareFuncVar() {
        checkDiscoveredProperties(
            method = "sliceCompareFuncVar",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().size < 2 && r.isPanic },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size >= 2 &&
                    r.long == if (minOf(a?.get(0).longValue(), a?.get(1).longValue()) == 5L) 3L else 1L
            },
        )
    }

    @Test
    @GoSample(method = "sliceSumMatrix")
    fun sliceSumMatrix() {
        checkDiscoveredProperties(
            method = "sliceSumMatrix",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult ->
                (a.orEmpty().size < 3 || (a?.get(0) as? List<*>).orEmpty().size < 3) &&
                    r.isPanic
            },
            { a: GoSlice, r: GoResult ->
                a.orEmpty().size >= 3 &&
                    (a?.get(0) as? List<*>).orEmpty().size >= 3 &&
                    r.long == nativeInt(a.orEmpty().sumOf { (it as? List<*>).longValues().sum() })
            },
        )
    }
}
