package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
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
}