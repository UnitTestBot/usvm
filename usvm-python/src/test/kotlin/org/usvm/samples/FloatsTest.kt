package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class FloatsTest : PythonTestRunnerForPrimitiveProgram("Floats") {
    @Test
    fun testFloatInput() {
        check1WithConcreteRun(
            constructFunction("float_input", listOf(PythonAnyType)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testComparison() {
        check1WithConcreteRun(
            constructFunction("comparison", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(7) {
                { _, res -> res.repr == (it + 1).toString() }
            }
        )
    }

    @Test
    fun testSimpleOperations() {
        check1WithConcreteRun(
            constructFunction("simple_operations", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(6) {
                { _, res -> res.repr == (it + 1).toString() }
            }
        )
    }

    @Test
    fun testRound() {
        check1WithConcreteRun(
            constructFunction("round", listOf(typeSystem.pythonFloat)),
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
    fun testInfComparison() {
        check1WithConcreteRun(
            constructFunction("inf_comparison", listOf(typeSystem.pythonFloat)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testInfinityOps() {
        allowPathDiversions = false
        check1WithConcreteRun(
            constructFunction("infinity_ops", listOf(typeSystem.pythonFloat)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.typeName != "str" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" },
                { _, res -> res.selfTypeName == "ZeroDivisionError" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testIntTrueDiv() {
        check2WithConcreteRun(
            constructFunction("int_true_div", listOf(typeSystem.pythonInt, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, y, res -> y.repr == "0" && res.selfTypeName == "ZeroDivisionError" },
                { _, _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testUnaryOps() {
        check1WithConcreteRun(
            constructFunction("unary_ops", listOf(typeSystem.pythonFloat)),
            eq(3),
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