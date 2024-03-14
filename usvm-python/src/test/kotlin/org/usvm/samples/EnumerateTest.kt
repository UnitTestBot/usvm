package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.time.Duration.Companion.seconds

class EnumerateTest: PythonTestRunnerForPrimitiveProgram(
    "Enumerate",
    UMachineOptions(stepLimit = 25U, timeout = 20.seconds),
    // allowPathDiversions = true
) {
    @Test
    fun testEnumerateOnAny() {
        check1WithConcreteRun(
            constructFunction("use_enumerate", listOf(PythonAnyType)),
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
    fun testEnumerateOnList() {
        check1WithConcreteRun(
            constructFunction("use_enumerate", listOf(typeSystem.pythonList)),
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
    fun testEnumerateOnTuple() {
        check1WithConcreteRun(
            constructFunction("use_enumerate", listOf(typeSystem.pythonTuple)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }
}