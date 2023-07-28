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
}