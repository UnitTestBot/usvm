package org.usvm.samples.runner

import org.junit.jupiter.api.Test
import org.usvm.samples.GoExecution
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.test.util.checkers.eq
import kotlin.test.assertFailsWith

class GoMethodTestRunnerTest {
    @Test
    fun acceptsInputDependentResultsForBothBranches() {
        val runner = FixtureRunner(listOf(execution(input = -1L, output = -1L), execution(input = 1L, output = 1L)))

        runner.checkBranchMatches()
    }

    @Test
    fun requiresEveryExpectedBranchToBeDiscovered() {
        val runner = FixtureRunner(listOf(execution(input = -1L, output = -1L)))

        assertFailsWith<IllegalStateException> { runner.checkBranchProperties() }
    }

    @Test
    fun rejectsAnAdditionalExecutionWithAnIncorrectResult() {
        val runner = FixtureRunner(
            listOf(
                execution(input = -1L, output = -1L),
                execution(input = 1L, output = 1L),
                execution(input = 2L, output = -1L),
            ),
        )

        assertFailsWith<IllegalArgumentException> { runner.checkBranchProperties() }
    }

    private fun execution(input: Long, output: Long): GoExecution = GoExecution(
        arguments = listOf(input),
        argumentsAfter = listOf(input),
        result = GoResult(value = output, isPanic = false),
    )

    private class FixtureRunner(executions: List<GoExecution>) : GoMethodTestRunner() {
        override val runner = { _: String, _: org.usvm.UMachineOptions -> executions }

        fun checkBranchMatches() = checkMatches(
            method = "fixture",
            analysisResultsNumberMatcher = eq(count = 2),
            { input: Number, result: GoResult -> input.toLong() < 0 && result.long == -1L },
            { input: Number, result: GoResult -> input.toLong() > 0 && result.long == 1L },
        )

        fun checkBranchProperties() = checkDiscoveredProperties(
            method = "fixture",
            analysisResultsNumberMatcher = org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults,
            { input: Number, result: GoResult -> input.toLong() < 0 && result.long == -1L },
            { input: Number, result: GoResult -> input.toLong() > 0 && result.long == 1L },
        )
    }
}
