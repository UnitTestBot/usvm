package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TupleTest: PythonTestRunnerForPrimitiveProgram("Tuple", UMachineOptions(stepLimit = 20U)) {
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
        val oldOptions = options
        options = UMachineOptions(stepLimit = 50U)
        timeoutPerRunMs = 2000
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("input_list_of_pairs", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.selfTypeName == "ValueError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
        allowPathDiversions = false
        options = oldOptions
    }

    @Test
    fun testLength() {
        check1WithConcreteRun(
            constructFunction("length", listOf(typeSystem.pythonTuple)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" },
            )
        )
    }

    @Test
    fun testSumOfTuple() {
        check1WithConcreteRun(
            constructFunction("sum_of_tuple", listOf(typeSystem.pythonTuple)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" },
            )
        )
    }

    @Test
    fun testGetItemSample() {
        check1WithConcreteRun(
            constructFunction("get_item_sample", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(4) {
                { _, res -> res.repr == (it + 1).toString() }
            }
        )
    }

    @Test
    fun testGetItemOfInput() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 80U)
        allowPathDiversions = true
        check2WithConcreteRun(
            constructFunction("get_item_of_input", listOf(PythonAnyType, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" },
                { _, _, res -> res.repr == "3" },
                { _, _, res -> res.repr == "4" },
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "IndexError" },
            )
        )
        options = oldOptions
        allowPathDiversions = false
    }

    @Test
    fun testUseCount() {
        check1WithConcreteRun(
            constructFunction("use_count", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testUseIndex() {
        check1WithConcreteRun(
            constructFunction("use_index", listOf(typeSystem.pythonTuple)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }
}