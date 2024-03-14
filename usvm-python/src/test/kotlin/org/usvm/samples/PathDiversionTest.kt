package org.usvm.samples

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.machine.interpreters.symbolic.operations.tracing.PathDiversionException
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class PathDiversionTest : PythonTestRunnerForPrimitiveProgram("PathDiversionExample") {
    private val function = constructFunction("pickle_path_diversion", listOf(typeSystem.pythonInt))
    @Test
    fun testAllowPathDiversion() {
        allowPathDiversions = true
        check1WithConcreteRun(
            function,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "4" }
            )
        )
    }
    @Test
    fun testForbidPathDiversion() {
        allowPathDiversions = false
        assertThrows<PathDiversionException> {
            check1(
                function,
                ignoreNumberOfAnalysisResults,
                /* invariants = */ emptyList(),
                /* propertiesToDiscover = */ emptyList()
            )
        }
    }
}