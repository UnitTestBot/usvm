package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq

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
}