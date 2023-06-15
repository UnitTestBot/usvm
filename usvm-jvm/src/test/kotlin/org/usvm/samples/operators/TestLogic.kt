package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.samples.TestRunner

class TestLogic : TestRunner() {
    @Test
    fun `Test complexWithLocals`() {
        run(
            Logic::complexWithLocals,
            { _, x, y, z, r -> r && (x.toLong() or y.toLong() or z) != 1337.toLong() },
            { _, x, y, z, r -> !r && (x.toLong() or y.toLong() or z) == 1337.toLong() },
        )
    }
}
