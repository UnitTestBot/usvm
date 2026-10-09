package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.longValues
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SliceMutationTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "sliceOverwrite")
    fun sliceOverwrite() {
        checkDiscoveredProperties(
            method = "sliceOverwrite",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().isEmpty() && r.long == -1L },
            { a: GoSlice, r: GoResult -> a.orEmpty().isNotEmpty() && r.long == 152L },
        )
    }

    @Test
    @GoSample(method = "sliceCustomOverwrite")
    fun sliceCustomOverwrite() {
        checkDiscoveredProperties(
            method = "sliceCustomOverwrite",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().isEmpty() && r.long == -1L },
            { a: GoSlice, r: GoResult -> a.orEmpty().isNotEmpty() && r.long == 152L },
        )
    }

    @Test
    @GoSample(method = "sliceSimple")
    fun sliceSimple() {
        checkDiscoveredProperties(
            method = "sliceSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult -> a.orEmpty().isEmpty() && r.isPanic },
            { a: GoSlice, r: GoResult -> a.orEmpty().isNotEmpty() && r.long == 5L },
        )
    }

    @Test
    @GoSample(method = "sliceCopySimple")
    fun sliceCopySimple() {
        checkDiscoveredProperties(
            method = "sliceCopySimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult ->
                r.isSuccess &&
                    r.list?.longValues() == a?.longValues()?.let { if (it.isEmpty()) it else listOf(5L) + it.drop(1) }
            },
        )
    }

    @Test
    @GoSample(method = "sliceCustomCopySimple")
    fun sliceCustomCopySimple() {
        checkDiscoveredProperties(
            method = "sliceCustomCopySimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { a: GoSlice, r: GoResult ->
                r.isSuccess &&
                    r.list?.longValues() == a?.longValues()?.let { if (it.isEmpty()) it else listOf(5L) + it.drop(1) }
            },
        )
    }
}
