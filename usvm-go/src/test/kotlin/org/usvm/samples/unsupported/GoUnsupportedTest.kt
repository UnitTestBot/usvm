package org.usvm.samples.unsupported

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoSample
import kotlin.test.assertTrue

class GoUnsupportedTest : GoMethodTestRunner(fixture = "regressions") {
    @Test
    @GoSample(method = "unsupportedGoroutine", fixture = "regressions")
    fun goroutinesAreRejectedAtEntry() = assertUnsupported(
        method = "unsupportedGoroutine",
        message = "unsupported instructions"
    )

    @Test
    @GoSample(method = "unsupportedCaller", fixture = "regressions")
    fun goroutinesAreRejectedInsideCalls() = assertUnsupported(
        method = "unsupportedCaller",
        message = "unsupported instructions"
    )

    @Test
    @GoSample(method = "symbolicStringEquality", fixture = "regressions")
    fun twoSymbolicStringLengthsAreRejected() = assertUnsupported(
        method = "symbolicStringEquality",
        message = "two symbolic lengths"
    )

    @Test
    @GoSample(method = "oversizedResolvedSlice", fixture = "regressions")
    fun oversizedSlicesAreRejectedWithoutTruncation() = assertUnsupported(
        method = "oversizedResolvedSlice",
        message = "within size limit"
    )

    private fun assertUnsupported(method: String, message: String) {
        val error = assertThrows<UnsupportedOperationException> { runner(method, options) }
        assertTrue(error.message.orEmpty().contains(message), error.message)
    }
}
