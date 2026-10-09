package org.usvm.samples.collections.slices

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.eq

class SymbolicSliceAliasTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "symbolicSliceAlias", fixture = "regressions")
    fun symbolicSliceAlias() {
        checkMatches(
            method = "symbolicSliceAlias",
            analysisResultsNumberMatcher = eq(count = 2),
            { value: Number, r: GoResult -> value.toLong() < 0 && r.long == 3L },
            { value: Number, r: GoResult -> value.toLong() >= 0 && r.long == 7L },
        )

        replayInputs(methodName = "symbolicSliceAlias", expectedExecutions = 2)
    }
}
