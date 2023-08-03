package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleListsTest : PythonTestRunner("/samples/SimpleLists.py") {

    private val functionSimpleListSample = constructFunction("simple_list_sample", listOf(pythonList, pythonInt))
    @Test
    fun testSimpleListSample() {
        check2WithConcreteRun(
            functionSimpleListSample,
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

    private val functionAllocatedList = constructFunction("allocated_list_sample", listOf(pythonInt))
    @Test
    fun testAllocatedList() {
        check1WithConcreteRun(
            functionAllocatedList,
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

    private val functionMixedAllocation = constructFunction("mixed_allocation", listOf(pythonInt, pythonInt))
    @Test
    fun testMixedAllocation() {
        check2WithConcreteRun(
            functionMixedAllocation,
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

    private val functionNegativeIndex = constructFunction("negative_index", listOf(pythonInt))
    @Test
    fun testNegativeIndex() {
        check1WithConcreteRun(
            functionNegativeIndex,
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { i, _ -> i.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    private val functionLongList = constructFunction("long_list", listOf(pythonInt))
    @Test
    fun testLongList() {
        check1WithConcreteRun(
            functionLongList,
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { i, _ -> i.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    private val functionSetItem = constructFunction("set_item", listOf(pythonList, pythonInt))
    @Test
    fun testSetItem() {
        check2WithConcreteRun(
            functionSetItem,
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

    private val functionMemoryModel = constructFunction("memory_model", listOf(pythonList, pythonList))
    @Test
    fun testMemoryModel() {
        check2WithConcreteRun(
            functionMemoryModel,
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

    private val functionPositiveAndNegativeIndex = constructFunction("positive_and_negative_index", listOf(pythonList, pythonInt))
    @Test
    fun testPositiveAndNegativeIndex() {
        check2WithConcreteRun(
            functionPositiveAndNegativeIndex,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { i, j, _ -> i.typeName == "list" && j.typeName == "int" },
            /* propertiesToDiscover = */ List(7) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    private val functionLenUsage = constructFunction("len_usage", listOf(pythonList))
    @Test
    fun testLenUsage() {
        check1WithConcreteRun(
            functionLenUsage,
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "list" && res.typeName == "int" },
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    private val functionSumOfElements = constructFunction("sum_of_elements", listOf(pythonList))
    @Test
    fun testSumOfElements() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 20U)
        check1WithConcreteRun(
            functionSumOfElements,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "list" && res.typeName == "int" },
            /* propertiesToDiscover = */ List(4) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
        options = oldOptions
    }

    private val functionForLoop = constructFunction("for_loop", listOf(pythonList))
    @Test
    fun testForLoop() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 20U)
        check1WithConcreteRun(
            functionForLoop,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "list" && res.typeName == "int" },
            /* propertiesToDiscover = */ List(3) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
        options = oldOptions
    }
}