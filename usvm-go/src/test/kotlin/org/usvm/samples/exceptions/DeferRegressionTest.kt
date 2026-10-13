package org.usvm.samples.exceptions

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class DeferRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "deferredArguments", fixture = "regressions")
    fun deferredArguments() = checkNative(method = "deferredArguments")

    @Test
    @GoSample(method = "repeatedDeferArguments", fixture = "regressions")
    fun repeatedDeferArguments() = checkNative(method = "repeatedDeferArguments")

    @Test
    @GoSample(method = "deferredStructArgument", fixture = "regressions")
    fun deferredStructArgument() = checkNative(method = "deferredStructArgument")

    @Test
    @GoSample(method = "recursiveDeferredArguments", fixture = "regressions")
    fun recursiveDeferredArguments() = checkNative(method = "recursiveDeferredArguments")

    @Test
    @GoSample(method = "reviewDeferSet", fixture = "regressions")
    fun deferredHelperMutatesThePointedValue() {
        checkParameterMutations(
            method = "reviewDeferSet",
            { execution -> execution.arguments[0] == null && execution.result.isPanic },
            { execution ->
                val pointer = execution.argumentsAfter[0] as? GoPointer
                execution.result.isSuccess && pointer?.value == execution.arguments[1]
            },
        )
    }

    @Test
    @GoSample(method = "recursiveDeferFrames", fixture = "regressions")
    fun recursionKeepsSeparateDeferredFrames() {
        checkDiscoveredProperties(
            method = "recursiveDeferFrames",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { depth: Number, result: GoResult -> depth.toLong() !in 0..2 && result.long == -1L },
            { depth: Number, result: GoResult -> depth.toLong() in 0..2 && result.long == depth.toLong() + 1 },
        )
    }
}
