package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForPrimitiveProgram
import org.usvm.test.util.checkers.eq

class DictsTest : PythonTestRunnerForPrimitiveProgram("Dicts") {
    @Test
    fun testFloatInput() {
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
}