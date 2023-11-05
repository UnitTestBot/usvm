package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MethodsTest: PythonTestRunnerForStructuredProgram("Methods", UMachineOptions(stepLimit = 20U)) {
    @Test
    fun testPointGetInfo() {
        check1WithConcreteRun(
            constructFunction("Point.get_info", List(1) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteTypes,
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
            compareConcolicAndConcreteTypes,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                {_, res -> res.repr == "'same'"},
                {_, res -> res.repr == "'x is less that y'"},
                {_, res -> res.repr == "'y is less that x'"}
            )
        )
    }
}