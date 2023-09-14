package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SlicesTest: PythonTestRunnerForStructuredProgram("Slices", UMachineOptions(stepLimit = 20U)) {
    @Test
    fun testFieldStart() {
        check1WithConcreteRun(
            constructFunction("field_start", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "slice" && res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "slice" && res.repr == "None" }
            )
        )
    }
}