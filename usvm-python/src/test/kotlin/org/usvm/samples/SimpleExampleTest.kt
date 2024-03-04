package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleExampleTest : PythonTestRunnerForPrimitiveProgram("SimpleExample") {

    @Test
    fun testManyBranches() {
        allowPathDiversions = true
        check3WithConcreteRun(
            constructFunction("many_branches", List(3) { typeSystem.pythonInt }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, y, z, res ->
                listOf(x, y, z, res).all { it.typeName == "int" }
            },
            /* propertiesToDiscover = */ List(10) { index ->
                { _, _, _, res -> res.repr == index.toString() }
            }
        )
        allowPathDiversions = false
    }

    @Test
    fun testMyAbs() {
        check1WithConcreteRun(
            constructFunction("my_abs", List(1) { typeSystem.pythonInt }),
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 0 && res.typeName == "int" },
                { x, res -> x.repr.toInt() == 0 && res.typeName == "str" },
                { x, res -> x.repr.toInt() < 0 && res.typeName == "int" },
            )
        )
    }

    @Test
    fun testSamplePickle() {
        check1WithConcreteRun(
            constructFunction("pickle_sample", List(1) { typeSystem.pythonInt }),
            eq(1),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { _, res -> res.typeName == "bytes" },
            /* propertiesToDiscover = */ emptyList()
        )
    }

    @Test
    fun testCall() {
        check1WithConcreteRun(
            constructFunction("call", List(1) { typeSystem.pythonInt }),
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 0 && res.typeName == "int" },
                { x, res -> x.repr.toInt() == 0 && res.typeName == "str" },
                { x, res -> x.repr.toInt() < 0 && res.typeName == "int" },
            )
        )
    }

    @Test
    fun testZeroDivision() {
        check1WithConcreteRun(
            constructFunction("zero_division", List(1) { typeSystem.pythonInt }),
            eq(1),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res.selfTypeName == "ZeroDivisionError" },
            /* propertiesToDiscover = */ listOf()
        )
    }

    @Test
    fun testZeroDivisionInBranch() {
        check1WithConcreteRun(
            constructFunction("zero_division_in_branch", List(1) { typeSystem.pythonInt }),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 100 && res.selfTypeName == "ZeroDivisionError" },
                { x, res -> x.repr.toInt() <= 100 && res.repr == x.repr }
            )
        )
    }

    @Test
    fun testBoolInput() {
        check1WithConcreteRun(
            constructFunction("bool_input", List(1) { typeSystem.pythonBool }),
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "bool" },
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testMixedInputTypes() {
        check2WithConcreteRun(
            constructFunction("mixed_input_types", listOf(typeSystem.pythonBool, typeSystem.pythonInt)),
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "bool" && y.typeName == "int" },
            /* propertiesToDiscover = */ List(3) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testSymbolicCall() {
        check1WithConcreteRun(
            constructFunction("symbolic_call", List(1) { typeSystem.pythonInt }),
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testSimpleLambda() {
        check1WithConcreteRun(
            PyUnpinnedCallable.constructLambdaFunction(listOf(typeSystem.pythonInt), "lambda x: 1 if x == 157 else 0"),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> res.repr == "1" && x.repr == "157" },
                { _, res -> res.repr == "0" }
            )
        )
    }

    @Test
    fun testInfiniteRecursion() {
        check0WithConcreteRun(
            constructFunction("infinite_recursion", emptyList()),
            eq(1),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf { res -> res.selfTypeName == "RecursionError" }
        )
    }

    private fun testRange(functionName: String) {
        val oldOption = options
        options = UMachineOptions(stepLimit = 5U)
        check1WithConcreteRun(
            constructFunction(functionName, listOf(typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
        options = oldOption
    }

    @Test
    fun testRange1() {
        testRange("range_1")
    }

    @Test
    fun testRange2() {
        testRange("range_2")
    }

    @Test
    fun testRange3() {
        testRange("range_3")
    }

    @Test
    fun testRange4() {
        testRange("range_4")
    }

    @Test
    fun testRange5() {
        testRange("range_5")
    }

    @Test
    fun testRange6() {
        testRange("range_6")
    }

    @Test
    fun testSimpleString() {
        check1WithConcreteRun(
            constructFunction("simple_str", listOf(PythonAnyType)),
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
    fun testIsNone() {
        check1WithConcreteRun(
            constructFunction("is_none", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testIsNotNone() {
        check1WithConcreteRun(
            constructFunction("is_not_none", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testCallWithDefault() {
        check1WithConcreteRun(
            constructFunction("call_with_default", listOf(typeSystem.pythonInt)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> res.repr == "None" && x.repr == "9" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testUnaryIntOps() {
        check1WithConcreteRun(
            constructFunction("unary_int_ops", listOf(typeSystem.pythonInt)),
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