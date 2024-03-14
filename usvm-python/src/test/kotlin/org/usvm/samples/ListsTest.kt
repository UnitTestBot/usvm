package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.time.Duration.Companion.seconds

class ListsTest : PythonTestRunnerForPrimitiveProgram(
    "Lists",
    UMachineOptions(stepLimit = 35U)
) {
    init {
        timeoutPerRunMs = 2000
    }
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
            /* invariants = */ listOf { x, _ -> x.typeName == "list" },
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
            /* invariants = */ listOf { x, _ -> x.typeName == "list" },
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

    private fun richcompareCheck(function: PyUnpinnedCallable) {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 15U)
        allowPathDiversions = true
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
        allowPathDiversions = false
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
        allowPathDiversions = true
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
        allowPathDiversions = false
    }

    @Test
    fun testDoubleSubscriptAndCompare() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 30U, timeout = 20.seconds)
        allowPathDiversions = true
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
        allowPathDiversions = false
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

    @Test
    fun testRepeat1() {
        check1WithConcreteRun(
            constructFunction("repeat_1", listOf(typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> res.selfTypeName == "IndexError" && x.repr == "0" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testRepeat2() {
        check1WithConcreteRun(
            constructFunction("repeat_2", listOf(typeSystem.pythonInt)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    @Test
    fun testListOfFloatPairs() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 60U)
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("input_list_of_float_pairs", listOf(PythonAnyType)),
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
        options = oldOptions
        allowPathDiversions = false
    }

    @Test
    fun testListConcat() {
        check2WithConcreteRun(
            constructFunction("list_concat", listOf(typeSystem.pythonList, typeSystem.pythonList)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "list" && y.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testPopUsage() {
        check1WithConcreteRun(
            constructFunction("pop_usage", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testPopUsageWithIndex() {
        check1WithConcreteRun(
            constructFunction("pop_usage_with_index", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testInsertUsage() {
        check2WithConcreteRun(
            constructFunction("insert_usage", listOf(typeSystem.pythonList, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, _, _ -> x.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testExtendUsage() {
        check2WithConcreteRun(
            constructFunction("extend_usage", listOf(typeSystem.pythonList, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, _, _ -> x.typeName == "list" },
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testClearUsage() {
        check1WithConcreteRun(
            constructFunction("clear_usage", listOf(typeSystem.pythonInt)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testIndexUsage() {
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("index_usage", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "ValueError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
        allowPathDiversions = false
    }

    @Test
    fun testReverseUsage() {
        val oldOptions = options
        allowPathDiversions = false
        options = UMachineOptions(stepLimit = 50U)
        check1WithConcreteRun(
            constructFunction("reverse_usage", listOf(typeSystem.pythonList)),
            ge(3),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.typeName == "tuple" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr.startsWith("(1, ") },
                { _, res -> res.repr.startsWith("(2, ") },
            )
        )
        options = oldOptions
    }

    @Test
    fun testContainsOp() {
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("contains_op", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
        allowPathDiversions = false
    }

    @Test
    fun testUseConstructor() {
        check1WithConcreteRun(
            constructFunction("use_constructor", listOf(PythonAnyType)),
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
    fun testListFromRange() {
        check3WithConcreteRun(
            constructFunction("list_from_range", List(3) { PythonAnyType }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, _, res -> res.repr == "None" }
            )
        )
    }

    @Test
    fun testUseSort() {
        val oldOptions = options
        options = UMachineOptions(stepLimit = 100U)
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("use_sort", listOf(typeSystem.pythonList)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
        allowPathDiversions = false
        options = oldOptions
    }

    @Test
    fun testUseCopy() {
        check1WithConcreteRun(
            constructFunction("use_copy", listOf(typeSystem.pythonList)),
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
    fun testUseRemove() {
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("use_remove", listOf(PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
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
}