package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.language.PythonInt
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SimpleExampleTest : PythonTestRunner("/samples/SimpleExample.py") {
    private val functionF = PythonUnpinnedCallable.constructCallableFromName(List(3) { PythonInt }, "f")
    private val functionMyAbs = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "my_abs")
    private val functionSamplePickle = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "pickle_sample")
    private val functionCall = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "call")
    private val functionZeroDivision = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "zero_division")
    private val functionZeroDivisionInBranch = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "zero_division_in_branch")

    @Test
    fun testF() {
        check3WithConcreteRun(
            functionF,
            ignoreNumberOfAnalysisResults,
            compareConcolicAndConcreteReprs,
            /* invariants = */ listOf { x, y, z, res ->
                listOf(x, y, z, res).all { it!!.typeName == "int" }
            },
            /* propertiesToDiscover = */ List(10) { index ->
                { _, _, _, res -> res!!.repr == index.toString() }
            }
        )
    }

    @Test
    fun testMyAbs() {
        check1WithConcreteRun(
            functionMyAbs,
            eq(3),
            compareConcolicAndConcreteReprs,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 0 && res!!.typeName == "int" },
                { x, res -> x.repr.toInt() == 0 && res!!.typeName == "str" },
                { x, res -> x.repr.toInt() < 0 && res!!.typeName == "int" },
            )
        )
    }

    @Test
    fun testSamplePickle() {
        check1WithConcreteRun(
            functionSamplePickle,
            eq(1),
            compareConcolicAndConcreteReprs,
            /* invariants = */ listOf { _, res -> res!!.typeName == "bytes" },
            /* propertiesToDiscover = */ emptyList()
        )
    }

    @Test
    fun testCall() {
        check1WithConcreteRun(
            functionCall,
            eq(3),
            compareConcolicAndConcreteReprs,
            /* invariants = */ listOf { x, _ -> x.typeName == "int" },
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 0 && res!!.typeName == "int" },
                { x, res -> x.repr.toInt() == 0 && res!!.typeName == "str" },
                { x, res -> x.repr.toInt() < 0 && res!!.typeName == "int" },
            )
        )
    }

    @Test
    fun testZeroDivision() {
        check1(
            functionZeroDivision,
            eq(1),
            /* invariants = */ listOf { x, res -> x.typeName == "int" && res == null },
            /* propertiesToDiscover = */ listOf()
        )
    }

    @Test
    fun testZeroDivisionInBranch() {
        check1(
            functionZeroDivisionInBranch,
            eq(2),
            /* invariants = */ listOf(),
            /* propertiesToDiscover = */ listOf(
                { x, res -> x.repr.toInt() > 100 && res == null },
                { x, res -> x.repr.toInt() <= 100 && res!!.repr == x.repr }
            )
        )
    }
}