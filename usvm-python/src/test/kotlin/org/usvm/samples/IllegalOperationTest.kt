package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.machine.utils.withAdditionalPaths
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class IllegalOperationTest : PythonTestRunnerForStructuredProgram("SimpleExample") {
    @Test
    @Disabled("Disabled until fix") // todo: fix python test
    fun testIllegalOperation() {
        assertThrows<IllegalOperationException> {
            check0(
                constructFunction("illegal_operation", emptyList()),
                ignoreNumberOfAnalysisResults,
                /* invariants = */ emptyList(),
                /* propertiesToDiscover = */ emptyList()
            )
        }
    }

    @Test
    @Disabled("Disabled until fix") // todo: fix python test
    fun testSettraceUsage() {
        assertThrows<IllegalOperationException> {
            check0(
                constructFunction("settrace_usage", emptyList()),
                ignoreNumberOfAnalysisResults,
                /* invariants = */ emptyList(),
                /* propertiesToDiscover = */ emptyList()
            )
        }
    }

    @Test
    @Disabled("Disabled until fix") // todo: fix python test
    fun testRemoveTracing() {
        assertThrows<IllegalOperationException> {
            check0(
                constructFunction("remove_tracing", emptyList()),
                ignoreNumberOfAnalysisResults,
                /* invariants = */ emptyList(),
                /* propertiesToDiscover = */ emptyList()
            )
        }
    }

    @Test
    fun testBadProgram() {
        assertThrows<IllegalOperationException> {
            withAdditionalPaths(program.additionalPaths, null) {
                program.getNamespaceOfModule("BadProgram")
            }
        }
    }
}