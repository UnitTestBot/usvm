package org.usvm.census

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class UnknownCallCensusRunnerTest {
    @Test
    fun `elapsed timeout preserves a partial failure`() {
        val outcome = methodOutcomeAfterTimeoutCheck(
            status = MethodStatus.PARTIAL,
            error = "Interpreter step failed",
            elapsed = 6.seconds,
            timeout = 5.seconds,
        )

        assertEquals(MethodStatus.PARTIAL, outcome.status)
        assertEquals("Interpreter step failed", outcome.error)
    }

    @Test
    fun `elapsed timeout marks a completed analysis as timed out`() {
        val outcome = methodOutcomeAfterTimeoutCheck(
            status = MethodStatus.COMPLETED,
            error = null,
            elapsed = 6.seconds,
            timeout = 5.seconds,
        )

        assertEquals(MethodStatus.TIMEOUT, outcome.status)
        assertEquals("Machine timeout reached", outcome.error)
    }
}
