package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class CompositeSliceRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "compositePointerFieldsRemainShared", fixture = "regressions")
    fun compositePointerFieldsRemainShared() = checkNative(method = "compositePointerFieldsRemainShared")

    @Test
    @GoSample(method = "sliceStructCopy", fixture = "regressions")
    fun sliceStructCopy() = checkNative(method = "sliceStructCopy")

    @Test
    @GoSample(method = "appendStructCopy", fixture = "regressions")
    fun appendStructCopy() = checkNative(method = "appendStructCopy")

    @Test
    @GoSample(method = "sliceArrayCopy", fixture = "regressions")
    fun sliceArrayCopy() = checkNative(method = "sliceArrayCopy")

    @Test
    @GoSample(method = "appendArrayCopy", fixture = "regressions")
    fun appendArrayCopy() = checkNative(method = "appendArrayCopy")

    @Test
    @GoSample(method = "overlapCompositeCopy", fixture = "regressions")
    fun overlapCompositeCopy() = checkNative(method = "overlapCompositeCopy")

    @Test
    @GoSample(method = "appendCompositeReuse", fixture = "regressions")
    fun appendCompositeReuse() = checkNative(method = "appendCompositeReuse")

    @Test
    @GoSample(method = "appendCompositeAllocate", fixture = "regressions")
    fun appendCompositeAllocate() = checkNative(method = "appendCompositeAllocate")

    @Test
    @GoSample(method = "narrowSignedIndex", fixture = "regressions")
    fun narrowSignedIndex() = checkNative(method = "narrowSignedIndex")

    @Test
    @GoSample(method = "negativeInt16Index", fixture = "regressions")
    fun negativeInt16Index() = checkNative(method = "negativeInt16Index")

    @Test
    @GoSample(method = "namedSliceAssertionZero", fixture = "regressions")
    fun namedSliceAssertionZero() = checkNative(method = "namedSliceAssertionZero")

    @Test
    @GoSample(method = "symbolicNarrowIndex", fixture = "regressions")
    fun narrowSignedInputsRequireNonnegativeIndices() {
        checkMatches(
            method = "symbolicNarrowIndex",
            analysisResultsNumberMatcher = eq(count = 2),
            { index: Number, result: GoResult -> index.toLong() < 0 && result.isPanic },
            { index: Number, result: GoResult -> index.toLong() >= 0 && result.long == 0L },
        )
    }

    @Test
    @GoSample(method = "symbolicCompositeCopy", fixture = "regressions")
    fun copyPreservesValuesWithSymbolicLengths() {
        checkDiscoveredProperties(
            method = "symbolicCompositeCopy",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { length: Number, result: GoResult -> length.toLong() !in 0..3 && result.long == -1L },
            { length: Number, result: GoResult ->
                length.toLong() in 0..3 && result.long == length.toLong() * 10 + 1
            },
        )
        replayInputs(methodName = "symbolicCompositeCopy", expectedExecutions = 6)
    }

    @Test
    @GoSample(method = "symbolicCompositeAppend", fixture = "regressions")
    fun appendPreservesValuesWithSymbolicLengths() {
        checkDiscoveredProperties(
            method = "symbolicCompositeAppend",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { length: Number, result: GoResult -> length.toLong() !in 0..3 && result.long == -1L },
            { length: Number, result: GoResult ->
                length.toLong() in 0..3 && result.long == length.toLong() * 10 + 1
            },
        )
        replayInputs(methodName = "symbolicCompositeAppend", expectedExecutions = 6)
    }
}
