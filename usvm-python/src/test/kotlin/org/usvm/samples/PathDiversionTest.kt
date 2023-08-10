package org.usvm.samples

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.machine.interpreters.operations.tracing.PathDiversionException
import org.usvm.language.types.pythonInt
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class AllowPathDiversionTest : PythonTestRunnerForPrimitiveProgram("TrickyExample", allowPathDiversions = true) {
    private val function = constructFunction("pickle_path_diversion", listOf(pythonInt))
    @Test
    fun testAllowPathDiversion() {
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
}

class ForbidPathDiversionTest : PythonTestRunnerForPrimitiveProgram("TrickyExample", allowPathDiversions = false) {
    private val function = constructFunction("pickle_path_diversion", listOf(pythonInt))
    @Test
    fun testForbidPathDiversion() {
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