package org.usvm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.model.Converter
import org.usvm.model.Parser
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GoUnsupportedTest {
    @Test
    fun goroutinesAreRejectedAtEntry() = assertUnsupported("unsupportedGoroutine", "unsupported instructions")

    @Test
    fun goroutinesAreRejectedInsideCalls() = assertUnsupported("unsupportedCaller", "unsupported instructions")

    @Test
    fun twoSymbolicStringLengthsAreRejected() = assertUnsupported("symbolicStringEquality", "two symbolic lengths")

    private fun assertUnsupported(method: String, message: String) {
        val pkg = Converter.unpack(Parser().deserialize(generatedGoFile("regressions/usvm_regressions.json").path))
        val options = UMachineOptions(timeout = 5.seconds, solverTimeout = 2.seconds, typeOperationsTimeout = 2.seconds)
        val customOptions = GoMachineOptions(failOnNotFullCoverage = true, uncoveredMethods = emptyList())

        GoMachine(GoProgram(listOf(pkg)), options, customOptions).use { machine ->
            val error = assertThrows<UnsupportedOperationException> { machine.analyzeAndResolve(pkg, method) }
            assertTrue(error.message.orEmpty().contains(message), error.message)
        }
    }
}
