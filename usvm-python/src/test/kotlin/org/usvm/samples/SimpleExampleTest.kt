package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonBool
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleExampleTest : PythonTestRunner("/samples/SimpleExample.py") {

    @Test
    fun testManyBranches() {
        check3WithConcreteRun(
            constructFunction("many_branches", List(3) { pythonInt }),
            ignoreNumberOfAnalysisResults,
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, y, z, res ->
                listOf(x, y, z, res).all { it.typeName == "int" }
            },
            /* propertiesToDiscover = */ List(10) { index ->
                { _, _, _, res -> res.repr == index.toString() }
            }
        )
    }

    @Test
    fun testMyAbs() {
        check1WithConcreteRun(
            constructFunction("my_abs", List(1) { pythonInt }),
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 0 && res.typeName == "int" },
                { x, res -> x.repr.toInt() == 0 && res.typeName == "str" },
                { x, res -> x.repr.toInt() < 0 && res.typeName == "int" },
            )
        )
    }

    @Test
    fun testSamplePickle() {
        check1WithConcreteRun(
            constructFunction("pickle_sample", List(1) { pythonInt }),
            eq(1),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { _, res -> res.typeName == "bytes" },
            /* propertiesToDiscover = */ emptyList()
        )
    }

    @Test
    fun testCall() {
        check1WithConcreteRun(
            constructFunction("call", List(1) { pythonInt }),
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 0 && res.typeName == "int" },
                { x, res -> x.repr.toInt() == 0 && res.typeName == "str" },
                { x, res -> x.repr.toInt() < 0 && res.typeName == "int" },
            )
        )
    }

    @Test
    fun testZeroDivision() {
        check1WithConcreteRun(
            constructFunction("zero_division", List(1) { pythonInt }),
            eq(1),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res.selfTypeName == "ZeroDivisionError" },
            /* propertiesToDiscover = */ listOf()
        )
    }

    @Test
    fun testZeroDivisionInBranch() {
        check1WithConcreteRun(
            constructFunction("zero_division_in_branch", List(1) { pythonInt }),
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 100 && res.selfTypeName == "ZeroDivisionError" },
                { x, res -> x.repr.toInt() <= 100 && res.repr == x.repr }
            )
        )
    }

    @Test
    fun testBoolInput() {
        check1WithConcreteRun(
            constructFunction("bool_input", List(1) { pythonBool }),
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "bool" },
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testMixedInputTypes() {
        check2WithConcreteRun(
            constructFunction("mixed_input_types", listOf(pythonBool, pythonInt)),
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "bool" && y.typeName == "int" },
            /* propertiesToDiscover = */ List(3) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    @Test
    fun testSymbolicCall() {
        check1WithConcreteRun(
            constructFunction("symbolic_call", List(1) { pythonInt }),
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { _, res -> res.selfTypeName == "AssertionError" },
                { _, res -> res.repr == "None" }
            )
        )
    }
}