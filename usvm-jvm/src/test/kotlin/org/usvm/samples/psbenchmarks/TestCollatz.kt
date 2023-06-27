package org.usvm.samples.psbenchmarks

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestCollatz : JavaMethodTestRunner() {

    @Test
    fun `Test collatzBomb1()`() {
        checkWithExceptionPropertiesMatches(
            Collatz::collatzBomb1,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isSuccess && r.getOrThrow() == 1 },
            { _, _, r -> r.isSuccess && r.getOrThrow() == 2 },
        )
    }
}
