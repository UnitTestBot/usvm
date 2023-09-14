package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SlicesTest: PythonTestRunnerForStructuredProgram("Slices", UMachineOptions(stepLimit = 20U)) {
    @Test
    fun testFieldStart() {
        check1WithConcreteRun(
            constructFunction("field_start", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "slice" && res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "slice" && res.repr == "None" }
            )
        )
    }
    @Test
    fun testFieldStop() {
        check1WithConcreteRun(
            constructFunction("field_stop", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "slice" && res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "slice" && res.repr == "None" }
            )
        )
    }
    @Test
    fun testFieldStep() {
        check1WithConcreteRun(
            constructFunction("field_step", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "slice" && res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "slice" && res.repr == "None" }
            )
        )
    }
    @Test
    fun testNoneFields() {
        check1WithConcreteRun(
            constructFunction("none_fields", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(6) {
                { x, res -> x.typeName == "slice" && res.repr == (it + 1).toString() }
            }
        )
    }

    @Test
    fun testSumOfSublist() {
        check1WithConcreteRun(
            constructFunction("sum_of_sublist", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(5) {
                { _, res -> res.repr == (it + 1).toString() }
            }
        )
    }

    @Test
    fun testSliceUsages() {
        val oldOptions = options
        allowPathDiversions = true
        options = UMachineOptions(stepLimit = 30U)
        check3WithConcreteRun(
            constructFunction("slice_usages", List(3) { typeSystem.pythonInt }),
            ge(20),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ emptyList()
        )
        options = oldOptions
        allowPathDiversions = false
    }
}