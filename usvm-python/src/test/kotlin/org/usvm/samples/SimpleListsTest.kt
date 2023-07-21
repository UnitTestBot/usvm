package org.usvm.samples


import org.junit.jupiter.api.Test
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ListsTest : PythonTestRunner("/samples/SimpleLists.py") {

    private val functionSimpleListSample = constructFunction("simple_list_sample", listOf(pythonList, pythonInt))
    @Test
    fun testSimpleListSample() {
        check2(
            functionSimpleListSample,
            ignoreNumberOfAnalysisResults,
            /* invariants = */ listOf { list, index, _ ->
                list.typeName == "list" && index.typeName == "int"
            },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res == null },
                { _, _, res -> res?.repr == "1" },
                { _, _, res -> res?.repr == "2" },
                { _, _, res -> res?.repr == "3" }
            )
        )
    }

    private val functionAllocatedList = constructFunction("allocated_list_sample", listOf(pythonInt))
    @Test
    fun testAllocatedList() {
        check1(
            functionAllocatedList,
            ignoreNumberOfAnalysisResults,
            /* invariants = */ listOf { index, _ ->
                index.typeName == "int"
            },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res == null },
                { _, res -> res?.repr == "1" },
                { _, res -> res?.repr == "2" },
                { _, res -> res?.repr == "3" },
                { _, res -> res?.repr == "4" }
            )
        )
    }

    private val functionMixedAllocation = constructFunction("mixed_allocation", listOf(pythonInt, pythonInt))
    @Test
    fun testMixedAllocation() {
        check2(
            functionMixedAllocation,
            ignoreNumberOfAnalysisResults,
            /* invariants = */ listOf { x, i, _ ->
                x.typeName == "int" && i.typeName == "int"
            },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res == null },
                { _, _, res -> res?.repr == "1" },
                { _, _, res -> res?.repr == "2" },
                { _, _, res -> res?.repr == "3" },
                { _, _, res -> res?.repr == "4" },
                { _, _, res -> res?.repr == "5" }
            )
        )
    }

    private val functionNegativeIndex = constructFunction("negative_index", listOf(pythonInt))
    @Test
    fun testNegativeIndex() {
        check1WithConcreteRun(
            functionNegativeIndex,
            eq(2),
            compareConcolicAndConcreteReprs,
            /* invariants = */ listOf { i, _ -> i.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res?.repr == "1" },
                { _, res -> res?.repr == "2" }
            )
        )
    }

    private val functionLongList = constructFunction("long_list", listOf(pythonInt))
    @Test
    fun testLongList() {
        check1WithConcreteRun(
            functionLongList,
            eq(2),
            compareConcolicAndConcreteReprs,
            /* invariants = */ listOf { i, _ -> i.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res?.repr == "1" },
                { _, res -> res?.repr == "2" }
            )
        )
    }

    private val functionSetItem = constructFunction("set_item", listOf(pythonList, pythonInt))
    @Test
    fun testSetItem() {
        check2(
            functionSetItem,
            ignoreNumberOfAnalysisResults,
            /* invariants = */ listOf { arr, x, _ -> arr.typeName == "list" && x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res == null },
                { _, _, res -> res?.repr == "0" },
                { _, _, res -> res?.repr == "1" },
                { _, _, res -> res?.repr == "2" }
            )
        )
    }

    private val functionMemoryModel = constructFunction("memory_model", listOf(pythonList, pythonList))
    @Test
    fun testMemoryModel() {
        check2(
            functionMemoryModel,
            ignoreNumberOfAnalysisResults,
            /* invariants = */ listOf { i, j, _ -> i.typeName == "list" && j.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res == null },
                { _, _, res -> res?.repr == "1" },
                { _, _, res -> res?.repr == "2" }
            )
        )
    }
}