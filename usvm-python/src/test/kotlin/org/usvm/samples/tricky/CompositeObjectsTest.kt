package org.usvm.samples.tricky

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class CompositeObjectsTest: PythonTestRunnerForStructuredProgram(
    "tricky.CompositeObjects",
    allowPathDiversions = false,
    options = UMachineOptions(stepLimit = 150U)
) {
    @Test
    fun testF() {
        check1WithConcreteRun(
            constructFunction("f", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" },
                { _, res -> res.repr == "3" },
            )
        )
    }
}