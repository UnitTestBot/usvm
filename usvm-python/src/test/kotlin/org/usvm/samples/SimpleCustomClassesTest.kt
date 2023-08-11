package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleCustomClassesTest: PythonTestRunnerForStructuredProgram("SimpleCustomClasses") {
    @Test
    fun testMatmulUsage() {
        check1WithConcreteRun(
            constructFunction("matmul_usage", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf { x, res ->
                x.typeName == "ClassWithMatmulAndAdd" && res.typeName == "ClassWithMatmulAndAdd"
            }
        )
    }
}