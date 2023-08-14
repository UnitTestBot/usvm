package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleCustomClassesTest: PythonTestRunnerForStructuredProgram("SimpleCustomClasses") {
    @Test
    fun testMatmulUsage() {
        check1WithConcreteRun(
            constructFunction("matmul_usage", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "ClassWithMatmulAndAdd" && res.typeName == "ClassWithMatmulAndAdd" },
                { x, res -> x.typeName == "ClassWithMatmulAndSub" && res.typeName == "ClassWithMatmulAndSub" }
            )
        )
    }

    @Test
    fun testMatmulAndAdd() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 2U)
        check1WithConcreteRun(
            constructFunction("matmul_and_add", List(1) { PythonAnyType }),
            eq(1),
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf { x, res ->
                x.typeName == "ClassWithMatmulAndAdd" && res.typeName == "ClassWithMatmulAndAdd"
            }
        )
        options = oldOptions
    }

    @Test
    fun testMatmulAddAndSub() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 4U)
        check1WithConcreteRun(
            constructFunction("matmul_add_and_sub", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "ClassWithMatmulAndAdd" && res.typeName == "ClassWithMatmulAndAdd" },
                { x, res -> x.typeName == "ClassWithMatmulAndSub" && res.typeName == "ClassWithMatmulAndSub" }
            )
        )
        options = oldOptions
    }
}