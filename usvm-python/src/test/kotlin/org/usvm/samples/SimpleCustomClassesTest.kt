package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleCustomClassesTest: PythonTestRunnerForStructuredProgram("SimpleCustomClasses", UMachineOptions(stepLimit = 20U)) {
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

    @Test
    fun testIterableOfMatmul() {
        check1WithConcreteRun(
            constructFunction("iterable_of_matmul", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testUseIntField() {
        check1WithConcreteRun(
            constructFunction("use_int_field", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AttributeError" },
                { x, res -> res.selfTypeName == "AssertionError" && x.typeName == "ClassWithField" },
                { x, res -> res.repr == "None" && x.typeName == "ClassWithField" }
            )
        )
    }

    @Test
    fun testUseDataclass() {
        check3WithConcreteRun(
            constructFunction("use_dataclass", List(3) { typeSystem.pythonInt }),
            eq(4),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(4) {
                { _, _, _, res -> res.repr == (it + 1).toString() }
            }
        )
    }
}