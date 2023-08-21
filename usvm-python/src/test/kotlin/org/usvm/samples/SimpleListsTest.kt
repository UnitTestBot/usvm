package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleListsTest : PythonTestRunnerForPrimitiveProgram("SimpleLists", UMachineOptions(stepLimit = 20U)) {
    @Test
    fun testSimpleListSample() {
        check2WithConcreteRun(
            constructFunction("simple_list_sample", listOf(typeSystem.pythonList, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { list, index, _ ->
                list.typeName == "list" && index.typeName == "int"
            },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" },
                { _, _, res -> res.repr == "3" }
            )
        )
    }

    @Test
    fun testAllocatedList() {
        check1WithConcreteRun(
            constructFunction("allocated_list_sample", listOf(typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { index, _ ->
                index.typeName == "int"
            },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" },
                { _, res -> res.repr == "3" },
                { _, res -> res.repr == "4" }
            )
        )
    }

    @Test
    fun testMixedAllocation() {
        check2WithConcreteRun(
            constructFunction("mixed_allocation", listOf(typeSystem.pythonInt, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, i, _ ->
                x.typeName == "int" && i.typeName == "int"
            },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" },
                { _, _, res -> res.repr == "3" },
                { _, _, res -> res.repr == "4" },
                { _, _, res -> res.repr == "5" }
            )
        )
    }

    @Test
    fun testNegativeIndex() {
        check1WithConcreteRun(
            constructFunction("negative_index", listOf(typeSystem.pythonInt)),
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { i, _ -> i.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testLongList() {
        check1WithConcreteRun(
            constructFunction("long_list", listOf(typeSystem.pythonInt)),
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { i, _ -> i.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testSetItem() {
        check2WithConcreteRun(
            constructFunction("set_item", listOf(typeSystem.pythonList, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { arr, x, _ -> arr.typeName == "list" && x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "0" },
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testMemoryModel() {
        check2WithConcreteRun(
            constructFunction("memory_model", listOf(typeSystem.pythonList, typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { i, j, _ -> i.typeName == "list" && j.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testPositiveAndNegativeIndex() {
        check2WithConcreteRun(
            constructFunction("positive_and_negative_index", listOf(typeSystem.pythonList, typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { i, j, _ -> i.typeName == "list" && j.typeName == "int" },
            /* propertiesToDiscover = */ List(7) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testLenUsage() {
        check1WithConcreteRun(
            constructFunction("len_usage", listOf(typeSystem.pythonList)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "list" && res.typeName == "int" },
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testSumOfElements() {
        check1WithConcreteRun(
            constructFunction("sum_of_elements", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "list" && res.typeName == "int" },
            /* propertiesToDiscover = */ List(4) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testForLoop() {
        check1WithConcreteRun(
            constructFunction("for_loop", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "list" && res.typeName == "int" },
            /* propertiesToDiscover = */ List(3) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testSimpleAssertion() {
        check1WithConcreteRun(
            constructFunction("simple_assertion", listOf(typeSystem.pythonList)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, _ -> x.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    private fun richcompareCheck(function: PythonUnpinnedCallable) {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 10U)
        check2WithConcreteRun(
            function,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "list" && y.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.repr == "None" }
            )
        )
        options = oldOptions
    }

    @Test
    fun testLt() {
        richcompareCheck(constructFunction("lt", listOf(typeSystem.pythonList, typeSystem.pythonList)))
    }
    @Test
    fun testGt() {
        richcompareCheck(constructFunction("gt", listOf(typeSystem.pythonList, typeSystem.pythonList)))
    }
    @Test
    fun testEq() {
        richcompareCheck(constructFunction("eq", listOf(typeSystem.pythonList, typeSystem.pythonList)))
    }
    @Test
    fun testNe() {
        richcompareCheck(constructFunction("ne", listOf(typeSystem.pythonList, typeSystem.pythonList)))
    }
    @Test
    fun testLe() {
        richcompareCheck(constructFunction("le", listOf(typeSystem.pythonList, typeSystem.pythonList)))
    }
    @Test
    fun testGe() {
        richcompareCheck(constructFunction("ge", listOf(typeSystem.pythonList, typeSystem.pythonList)))
    }

    @Test
    fun testAddAndCompare() {
        check2WithConcreteRun(
            constructFunction("add_and_compare", listOf(typeSystem.pythonList, typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "list" && y.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testDoubleSubscriptAndCompare() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 15U)
        check2WithConcreteRun(
            constructFunction("double_subscript_and_compare", listOf(typeSystem.pythonList, typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "list" && y.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "IndexError" },
                { _, _, res -> res.repr == "None" }
            )
        )
        options = oldOptions
    }

    @Test
    fun testListAppend() {
        check1WithConcreteRun(
            constructFunction("list_append", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { x, res -> x.repr == "127" && res.repr == "None" }
            )
        )
    }
}