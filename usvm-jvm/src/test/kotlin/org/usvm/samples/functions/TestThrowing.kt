package org.usvm.samples.functions

import org.junit.jupiter.api.Test
import org.usvm.TestRunner

class TestThrowing : TestRunner() {
    @Test
    fun `Test throwSometimes`() {
        runWithException(
            Throwing::throwSometimes,
            { _, x, r -> x == 1 && r.isFailure },
            { _, x, r -> x != 1 && r.isSuccess },
        )
    }

}