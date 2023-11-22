package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class DictsTest : PythonTestRunnerForPrimitiveProgram("Dicts") {
    @Test
    fun testExpectDict() {
        check1WithConcreteRun(
            constructFunction("expect_dict", listOf(PythonAnyType)),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" }
            )
        )
    }

    @Test
    fun testInputDictStrGetItem() {
        check1WithConcreteRun(
            constructFunction("input_dict_str_get_item", listOf(typeSystem.pythonDict)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.selfTypeName == "KeyError" }
            )
        )
    }

    @Test
    fun testInputDictVirtualGetItem() {
        check2WithConcreteRun(
            constructFunction("input_dict_virtual_get_item", listOf(typeSystem.pythonDict, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "KeyError" }
            )
        )
    }

    @Test
    fun testAllocateDict() {
        check2WithConcreteRun(
            constructFunction("allocate_dict", listOf(PythonAnyType, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "KeyError" }
            )
        )
    }

    @Test
    fun testInputDictIntGetItem() {
        check1WithConcreteRun(
            constructFunction("input_dict_int_get_item", listOf(typeSystem.pythonDict)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.repr == "None" },
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.selfTypeName == "KeyError" }
            )
        )
    }

    @Test
    fun testAllocateDictWithIntKey() {
        check2WithConcreteRun(
            constructFunction("allocate_dict_with_int_key", listOf(PythonAnyType, PythonAnyType)),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "None" },
                { _, _, res -> res.selfTypeName == "AssertionError" },
                { _, _, res -> res.selfTypeName == "KeyError" }
            )
        )
    }
}