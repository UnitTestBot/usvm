package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.machine.types.PythonAnyType
import org.usvm.runner.PythonTestRunnerForStructuredProgram
import org.usvm.test.util.checkers.eq

class SimpleUsageOfModulesTest: PythonTestRunnerForStructuredProgram("sample_submodule.SimpleUsageOfModules") {
    @Test
    fun testConstructClassInstance() {
        check1WithConcreteRun(
            constructFunction("construct_class_instance", List(1) { typeSystem.pythonInt }),
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
            constructFunction("inner_import", List(1) { typeSystem.pythonInt }),
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

    @Test
    fun testSimpleClassIsinstance() {
        check1WithConcreteRun(
            constructFunction("simple_class_isinstance", List(1) { PythonAnyType }),
            eq(4),
            standardConcolicAndConcreteChecks,
            /* invariants = */ emptyList(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.typeName == "int" && res.repr == "2" },
                { x, res -> x.typeName == "int" && res.selfTypeName == "AssertionError" },
                { x, res -> x.typeName == "SimpleClass" && res.repr == "1" },
                { _, res -> res.repr == "3" }
            )
        )
    }
}