package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MethodsTest: PythonTestRunnerForStructuredProgram("Methods", UMachineOptions(stepLimit = 20U)) {
    @Test
    fun testPointGetInfo() {
        check1WithConcreteRun(
            constructFunction("Point.get_info", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, res -> res.repr == "'same'"},
                {_, res -> res.repr == "'x is less that y'"},
                {_, res -> res.repr == "'y is less that x'"}
            )
        )
    }

    @Test
    fun testExternalFunction() {
        check1WithConcreteRun(
            constructFunction("external_function", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, res -> res.repr == "'same'"},
                {_, res -> res.repr == "'x is less that y'"},
                {_, res -> res.repr == "'y is less that x'"}
            )
        )
    }

    @Test
    fun testSetAttribute() {
        check2WithConcreteRun(
            constructFunction("set_attribute", listOf(PythonAnyType, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {p, _, res -> res.repr == "1" && p.typeName == "Point"},
                {p, x, res -> res.repr == "2" && p.typeName == "Point" && x.repr == "239"},
                {p, _, res -> res.repr == "3" && p.typeName == "Point"},
            )
        )
    }

    @Test
    fun testCallOfObjectConstructor() {
        check1WithConcreteRun(
            constructFunction("call_of_object_constructor", List(1) { typeSystem.pythonInt }),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, res -> res.repr == "None"},
                {_, res -> res.selfTypeName == "AssertionError"}
            )
        )
    }

    @Test
    fun testCallOfSlotConstructor() {
        check2WithConcreteRun(
            constructFunction("call_of_slot_constructor", listOf(typeSystem.pythonInt, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, _, res -> res.repr == "'same'"},
                {_, _, res -> res.repr == "'x is less that y'"},
                {_, _, res -> res.repr == "'y is less that x'"}
            )
        )
    }


    @Test
    fun testCallOfSlotConstructorWithNamedArgs() {
        check2WithConcreteRun(
            constructFunction("call_of_slot_constructor_with_named_args", listOf(typeSystem.pythonInt, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, _, res -> res.repr == "'same'"},
                {_, _, res -> res.repr == "'x is less that y'"},
                {_, _, res -> res.repr == "'y is less that x'"}
            )
        )
    }

    @Test
    fun testConstructorWithDefaultValues() {
        check2WithConcreteRun(
            constructFunction("constructor_with_default_values", listOf(typeSystem.pythonInt, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, _, res -> res.repr == "1"},
                {_, _, res -> res.repr == "2"},
                {_, _, res -> res.repr == "3"}
            )
        )
    }

    @Test
    fun testPoint2Inference() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 6U)
        check1WithConcreteRun(
            constructFunction("point2_inference", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf { x, _ -> x.typeName == "Point2" }
        )
        options = oldOptions
    }

    @Test
    fun testUseProperty() {
        check1WithConcreteRun(
            constructFunction("use_property", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, res -> res.repr == "None"},
                {_, res -> res.selfTypeName == "AssertionError"}
            )
        )
    }
}