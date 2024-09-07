package org.usvm

import org.junit.jupiter.api.Test
import org.usvm.model.Converter
import org.usvm.model.Parser
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JacoDbTest {
    @Test
    fun testMax2() {
        test("max2")
    }

    @Test
    fun testMax2Anon() {
        test("max2Anon")
    }

    @Test
    fun testMax2Closure() {
        test("max2Closure")
    }

    @Test
    fun testMax3() {
        test("max3")
    }

    @Test
    fun testMax3Call() {
        test("max3Call")
    }

    @Test
    fun testInc() {
        test("inc")
    }

    @Test
    fun testLoopSimple() {
        test("loopSimple")
    }

    @Test
    fun testLoopIf() {
        test("loopIf")
    }

    @Test
    fun testLoopInfinite() {
        test("loopInfinite")
    }

    @Test
    fun testLoopInner() {
        test("loopInner")
    }

    @Test
    fun testLoopCollatz() {
        test("loopCollatz")
    }

    @Test
    fun testLoopSum() {
        test("loopSum")
    }

    @Test
    fun testPointerSimple() {
        test("pointerSimple")
    }

    @Test
    fun testPointerOther() {
        test("pointerOther")
    }

    @Test
    fun testPointerAnother() {
        test("pointerAnother")
    }

    @Test
    fun testSliceSimple() {
        test("sliceSimple")
    }

    @Test
    fun testSliceOverwrite() {
        test("sliceOverwrite")
    }

    @Test
    fun testSliceAlloc() {
        test("sliceAlloc")
    }

    @Test
    fun testSliceFirst() {
        test("sliceFirst")
    }

    @Test
    fun testSliceSum() {
        test("sliceSum")
    }

    @Test
    fun testSliceCompare() {
        test("sliceCompare")
    }

    @Test
    fun testMapAlloc() {
        test("mapAlloc")
    }

    @Test
    fun testMapLookup() {
        test("mapLookup")
    }

    @Test
    fun testMapLookupComma() {
        test("mapLookupComma")
    }

    @Test
    fun testMapLookupCommaReturn() {
        test("mapLookupCommaReturn")
    }

    @Test
    fun testMapUpdate() {
        test("mapUpdate")
    }

    private val options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 1000000L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )

    private fun test(name: String) {
        val stopwatch = measureTimeMillis {
            val pkg = Converter.unpackPackage(Parser().deserialize("out/usvm_examples.json"))
            val machine = GoMachine(pkg, options)
            println(machine.analyzeAndResolve(name))
        }

        println(stopwatch)
    }
}