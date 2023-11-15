package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TrickyExamplesTest: PythonTestRunnerForStructuredProgram(
    "Tricky",
    UMachineOptions(stepLimit = 200U),
    allowPathDiversions = true
) {
    @Disabled
    @Test
    fun testSquareMatrix() {
        check2WithConcreteRun(
            constructFunction("square_matrix", listOf(PythonAnyType, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.repr == "'Success'" }
            )
        )
    }
}