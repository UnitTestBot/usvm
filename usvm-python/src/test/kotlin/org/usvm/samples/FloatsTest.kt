package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.PythonAnyType
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
}