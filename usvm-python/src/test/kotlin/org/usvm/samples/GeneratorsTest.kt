package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class GeneratorsTest : PythonTestRunnerForPrimitiveProgram("Generators") {
    @Test
    fun testGeneratorUsage() {
        check1WithConcreteRun(
            constructFunction("generator_usage", listOf(typeSystem.pythonInt)),
            eq(3),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "'one'" },
                { _, res -> res.repr == "'two'" },
                { _, res -> res.repr == "'other'" }
            )
        )
    }

    @Test
    fun testSimpleGenerator() {
        check1(
            constructFunction("simple_generator", listOf(typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "['one']" },
                { _, res -> res.repr == "['two']" },
                { _, res -> res.repr == "['other']" }
            )
        )
    }
}