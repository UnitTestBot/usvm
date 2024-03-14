package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SetsTest: PythonTestRunnerForPrimitiveProgram("Sets", UMachineOptions(stepLimit = 40U)) {
    @Test
    fun testExpectSet() {
        check1WithConcreteRun(
            constructFunction("expect_set", listOf(PythonAnyType)),
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
    fun testUseConstructorWithArg() {
        check1WithConcreteRun(
            constructFunction("use_constructor_with_arg", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf { _, res -> res.repr == "None" }
        )
    }

    @Test
    fun testInputSetIntCheck() {
        check1WithConcreteRun(
            constructFunction("input_set_int_check", listOf(typeSystem.pythonSet)),
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
    fun testInputSetStrCheck() {
        check1WithConcreteRun(
            constructFunction("input_set_str_check", listOf(typeSystem.pythonSet)),
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
    fun testInputSetVirtualCheck() {
        check2WithConcreteRun(
            constructFunction("input_set_virtual_check", listOf(typeSystem.pythonSet, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testConstructSetWithCall() {
        check1WithConcreteRun(
            constructFunction("construct_set_with_call", listOf(typeSystem.pythonInt)),
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
    fun testAddStrToSet() {
        check1WithConcreteRun(
            constructFunction("add_str_to_set", listOf(PythonAnyType)),
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
    fun testEmptyCheck() {
        check1WithConcreteRun(
            constructFunction("empty_check", listOf(typeSystem.pythonSet)),
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
    fun testConstructSetWithSyntax() {
        check2WithConcreteRun(
            constructFunction("construct_set_with_syntax", listOf(PythonAnyType, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }
}