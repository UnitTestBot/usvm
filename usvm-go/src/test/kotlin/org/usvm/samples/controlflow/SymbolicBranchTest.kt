package org.usvm.samples.controlflow

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.eq

class SymbolicBranchTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "symbolicBranch", fixture = "regressions")
    fun symbolicBranch() {
        checkMatches(
            method = "symbolicBranch",
            analysisResultsNumberMatcher = eq(count = 3),
            { value: Number, r: GoResult -> value.toLong() < 0 && r.long == -1L },
            { value: Number, r: GoResult -> value.toLong() == 0L && r.long == 0L },
            { value: Number, r: GoResult -> value.toLong() > 0 && r.long == 1L },
        )

        replayInputs(methodName = "symbolicBranch", expectedExecutions = 3)
    }
}
