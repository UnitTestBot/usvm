package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.PythonAnyType
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleTypeInferenceTest: PythonTestRunner("/samples/SimpleTypeInference.py") {
    private val functionBoolInput = constructFunction("bool_input", List(1) { PythonAnyType })
    @Test
    fun testBoolInput() {
        check1WithConcreteRun(
            functionBoolInput,
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    private val functionTwoArgs = constructFunction("two_args", List(2) { PythonAnyType })
    @Test
    fun testTwoArgs() {
        check2WithConcreteRun(
            functionTwoArgs,
            ge(4),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ List(4) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    private val functionListOfInt = constructFunction("list_of_int", List(1) { PythonAnyType })
    @Test
    fun testListOfInt() {
        check1WithConcreteRun(
            functionListOfInt,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.selfTypeName != "TypeError" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" },
                { _, res -> res.repr == "3" }
            )
        )
    }

    private val functionDoubleSubscript = constructFunction("double_subscript", listOf(PythonAnyType))
    @Test
    fun testDoubleSubscript() {
        check1WithConcreteRun(
            functionDoubleSubscript,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.selfTypeName != "TypeError" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "IndexError" },
                { _, res -> res.repr == "1" },
                { _, res -> res.repr == "2" }
            )
        )
    }

    private val functionSimpleComparison = constructFunction("simple_comparison", List(2) { PythonAnyType })
    @Test
    fun testSimpleComparison() {
        check2WithConcreteRun(
            functionSimpleComparison,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { _, _, res -> res.repr == "1" },
                { _, _, res -> res.repr == "2" },
                { _, _, res -> res.repr == "3" }
            )
        )
    }

    private val functionIsinstanceSample = constructFunction("isinstance_sample", List(1) { PythonAnyType })
    @Test
    fun testIsinstanceSample() {
        check1WithConcreteRun(
            functionIsinstanceSample,
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { _, res -> res.typeName == "int" },
            /* propertiesToDiscover = */ List(4) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }
}