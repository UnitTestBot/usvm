package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.pythonInt
import org.usvm.test.util.checkers.eq

class SimpleUsageOfModulesTest: PythonTestRunner("sample_submodule.SimpleUsageOfModules") {
    @Test
    fun testConstructClassInstance() {
        check1WithConcreteRun(
            constructFunction("construct_class_instance", List(1) { pythonInt }),
            eq(2),
            compareConcolicAndConcreteTypes,
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res.typeName == "SimpleClass" },
            /* propertiesToDiscover = */ listOf(
                { x, _ -> x.repr.toInt() >= 0 },
                { x, _ -> x.repr.toInt() < 0 }
            )
        )
    }

    @Test
    fun testInnerImport() {
        allowPathDiversions = true
        check1WithConcreteRun(
            constructFunction("inner_import", List(1) { pythonInt }),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, _ -> x.repr.toInt() >= 0 },
                { x, _ -> x.repr.toInt() < 0 }
            )
        )
        allowPathDiversions = false
    }
}