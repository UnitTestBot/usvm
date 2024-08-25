package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleTypeInferenceTest: PythonTestRunnerForPrimitiveProgram("SimpleTypeInference", UMachineOptions(stepLimit = 30U)) {
    @Test
    fun testBoolInput() {
        check1WithConcreteRun(
            constructFunction("bool_input", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testTwoArgs() {
        check2WithConcreteRun(
            constructFunction("two_args", List(2) { PythonAnyType }),
            ge(4),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(4) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testListOfInt() {
        check1WithConcreteRun(
            constructFunction("list_of_int", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.selfTypeName != "TypeError" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" },
                { _, res -> res.repr == "3" }
            )
        )
    }

    @Test
    fun testDoubleSubscript() {
        check1WithConcreteRun(
            constructFunction("double_subscript", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.selfTypeName != "TypeError" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testSimpleComparison() {
        check2WithConcreteRun(
            constructFunction("simple_comparison", List(2) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" },
                { _, _, res -> res.repr == "3" }
            )
        )
    }

    @Test
    fun testIsinstanceSample() {
        check1WithConcreteRun(
            constructFunction("isinstance_sample", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.typeName == "int" },
            /* propertiesToDiscover = */ List(4) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testListConcatUsage() {
        check2WithConcreteRun(
            constructFunction("list_concat_usage", List(2) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testLenUsage() {
        check1WithConcreteRun(
            constructFunction("len_usage", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.typeName == "int" },
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testIteration() {
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("iteration", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
        allowPathDiversions = false
    }

    @Test
    fun testAddAndCompare() {
        val oldOptions = options
        allowPathDiversions = true
        options = UMachineOptions(stepLimit = 200U)
        timeoutPerRunMs = 500
        check2WithConcreteRun(
            constructFunction("add_and_compare", List(2) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "None" }
            )
        )
        allowPathDiversions = false
        options = oldOptions
    }

    @Test
    fun testMultiplyAndCompare() {
        allowPathDiversions = true
        val oldOptions = options
        options = UMachineOptions(stepLimit = 350U)
        timeoutPerRunMs = 500
        check2WithConcreteRun(
            constructFunction("multiply_and_compare", List(2) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "None" }
            )
        )
        options = oldOptions
        allowPathDiversions = false
    }

    @Test
    fun testSubscriptAndIsinstance() {
        check1WithConcreteRun(
            constructFunction("subscript_and_isinstance", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" },
                { _, res -> res.repr == "3" },
                { _, res -> res.repr == "4" }
            )
        )
    }

    @Test
    fun testRangeLoop() {
        check1WithConcreteRun(
            constructFunction("range_loop", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testSumUsage() {
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("sum_usage", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "list" && res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "list" && res.repr == "None" }
            )
        )
        allowPathDiversions = false
    }

    @Test
    fun testUseStrEq() {
        check1WithConcreteRun(
            constructFunction("use_str_eq", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "str" && res.repr == "None" },
            )
        )
    }

    @Test
    fun testUseStrNeq() {
        check1WithConcreteRun(
            constructFunction("use_str_neq", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "str" && res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" },
            )
        )
    }
}