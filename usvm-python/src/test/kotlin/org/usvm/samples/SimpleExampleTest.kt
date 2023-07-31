package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonBool
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleExampleTest : PythonTestRunner("/samples/SimpleExample.py") {

    private val functionManyBranches = constructFunction("many_branches", List(3) { pythonInt })
    @Test
    fun testManyBranches() {
        check3WithConcreteRun(
            functionManyBranches,
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

    private val functionMyAbs = constructFunction("my_abs", List(1) { pythonInt })
    @Test
    fun testMyAbs() {
        check1WithConcreteRun(
            functionMyAbs,
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

    private val functionSamplePickle = constructFunction("pickle_sample", List(1) { pythonInt })
    @Test
    fun testSamplePickle() {
        check1WithConcreteRun(
            functionSamplePickle,
            eq(1),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { _, res -> res.typeName == "bytes" },
            /* propertiesToDiscover = */ emptyList()
        )
    }

    private val functionCall = constructFunction("call", List(1) { pythonInt })
    @Test
    fun testCall() {
        check1WithConcreteRun(
            functionCall,
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

    private val functionZeroDivision = constructFunction("zero_division", List(1) { pythonInt })
    @Test
    fun testZeroDivision() {
        check1WithConcreteRun(
            functionZeroDivision,
            eq(1),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res.selfTypeName == "ZeroDivisionError" },
            /* propertiesToDiscover = */ listOf()
        )
    }

    private val functionZeroDivisionInBranch = constructFunction("zero_division_in_branch", List(1) { pythonInt })
    @Test
    fun testZeroDivisionInBranch() {
        check1WithConcreteRun(
            functionZeroDivisionInBranch,
            eq(2),
            standardConcolicAndConcreteChecks,
            /* invariants = */ listOf(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 100 && res.selfTypeName == "ZeroDivisionError" },
                { x, res -> x.repr.toInt() <= 100 && res.repr == x.repr }
            )
        )
    }

    private val functionBoolInput = constructFunction("bool_input", List(1) { pythonBool })
    @Test
    fun testBoolInput() {
        check1WithConcreteRun(
            functionBoolInput,
            eq(2),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, _ -> x.typeName == "bool" },
            /* propertiesToDiscover = */ List(2) { index ->
                { _, res -> res.repr == (index + 1).toString() }
            }
        )
    }

    private val functionMixedInputTypes = constructFunction("mixed_input_types", listOf(pythonBool, pythonInt))
    @Test
    fun testMixedInputTypes() {
        check2WithConcreteRun(
            functionMixedInputTypes,
            eq(3),
            compareConcolicAndConcreteReprsIfSuccess,
            /* invariants = */ listOf { x, y, _ -> x.typeName == "bool" && y.typeName == "int" },
            /* propertiesToDiscover = */ List(3) { index ->
                { _, _, res -> res.repr == (index + 1).toString() }
            }
        )
    }
}