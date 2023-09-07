package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleTupleTest: PythonTestRunnerForPrimitiveProgram("SimpleTuple", UMachineOptions(stepLimit = 20U)) {
    @Test
    fun testTupleConstructAndIter() {
        check1WithConcreteRun(
            constructFunction("tuple_construct_and_iter", listOf(PythonAnyType)),
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
    fun testTupleUnpack() {
        check1WithConcreteRun(
            constructFunction("tuple_unpack", listOf(PythonAnyType)),
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
    fun testInputListOfPairs() {
        check1WithConcreteRun(
            constructFunction("input_list_of_pairs", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.selfTypeName == "ValueError" }
            )
        )
    }
}