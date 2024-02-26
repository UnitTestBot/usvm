package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.util.Path
import kotlin.time.Duration

class GoMachineTest {
    private val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)))

    @Test
    fun testMax() {
        val results = machine.analyzeAndResolve(Path.getProgram("if.go"), "max2", false)
        println(results)
    }

    @Test
    fun testMin() {
        val results = machine.analyzeAndResolve(Path.getProgram("if.go"), "min2", false)
        println(results)
    }

    @Test
    fun testMin3() {
        val machine =
            GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE))
        val results = machine.analyzeAndResolve(Path.getProgram("call.go"), "min3", false)
        println(results)
    }

    @Test
    fun testAdd() {
        val results = machine.analyzeAndResolve(Path.getProgram("if.go"), "add", false)
        println(results)
    }

    @Test
    fun testGcd() {
        val results = machine.analyzeAndResolve(Path.getProgram("if.go"), "gcd", false)
        println(results)
    }

    @Test
    fun testLoop() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.BFS)))
        val results = machine.analyzeAndResolve(Path.getProgram("loop.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopHard() {
        val machine = GoMachine(
            UMachineOptions(listOf(PathSelectionStrategy.BFS), timeout = Duration.INFINITE),
        )
        val results = machine.analyzeAndResolve(Path.getProgram("loop.go"), "loop2", false)
        println(results)
    }

    @Test
    fun testLoopInfinite() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.DFS),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
        )
        val results = machine.analyzeAndResolve(Path.getProgram("loop.go"), "infinite", false)
        println(results)
    }

    @Test
    fun testLoopCollatz() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.FORK_DEPTH),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
        )
        val results = machine.analyzeAndResolve(Path.getProgram("loop.go"), "collatz", false)
        println(results)
    }

    @Test
    fun testSumArray() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH)))
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "sumArray", false)
        println(results)
    }

    @Test
    fun testFirstArray() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "first", false)
        println(results)
    }

    @Test
    fun testStruct() {
        val results = machine.analyzeAndResolve(Path.getProgram("struct.go"), "isOld", false)
        println(results)
    }

    @Test
    fun testStructFieldSet() {
        val results = machine.analyzeAndResolve(Path.getProgram("struct.go"), "setPerfectAge", false)
        println(results)
    }

    @Test
    fun testStructValueReceiver() {
        val machine =
            GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE))
        val results = machine.analyzeAndResolve(Path.getProgram("struct.go"), "isYoung", false)
        println(results)
    }

    @Test
    fun testStructValueReceiverString() {
        val machine =
            GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE))
        val results = machine.analyzeAndResolve(Path.getProgram("struct.go"), "getStringGender", false)
        println(results)
    }

    @Test
    fun testMapLookup() {
        val results = machine.analyzeAndResolve(Path.getProgram("map.go"), "lookup", false)
        println(results)
    }

    @Test
    fun testMapLookupCommaOk() {
        val results = machine.analyzeAndResolve(Path.getProgram("map.go"), "lookupComma", false)
        println(results)
    }

    @Test
    fun testMapLookupCommaOkReturn() {
        val results = machine.analyzeAndResolve(Path.getProgram("map.go"), "lookupCommaReturn", false)
        println(results)
    }

    @Test
    fun testMapUpdate() {
        val results = machine.analyzeAndResolve(Path.getProgram("map.go"), "update", false)
        println(results)
    }

    @Test
    fun testPointer() {
        val results = machine.analyzeAndResolve(Path.getProgram("pointer.go"), "pointer", false)
        println(results)
    }

    @Test
    fun testPanic() {
        val results = machine.analyzeAndResolve(Path.getProgram("panic.go"), "panicSimple", false)
        println(results)
    }

    @Test
    fun testCountSort() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), timeout = Duration.INFINITE))
        val results = machine.analyzeAndResolve(Path.getProgram("sort.go"), "count", false)
        println(results)
    }

    @Test
    fun testBubbleSort() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), timeout = Duration.INFINITE))
        val results = machine.analyzeAndResolve(Path.getProgram("sort.go"), "bubble", false)
        println(results)
    }

    @Test
    fun testBubbleSortCast() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), timeout = Duration.INFINITE))
        val results = machine.analyzeAndResolve(Path.getProgram("sort.go"), "bubbleCast", false)
        println(results)
    }

    @Test
    fun testMakeSlice() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "alloc", false)
        println(results)
    }

    @Test
    fun testMakeMap() {
        val results = machine.analyzeAndResolve(Path.getProgram("map.go"), "alloc", false)
        println(results)
    }

    @Test
    fun testArrayOverwrite() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "overwrite", false)
        println(results)
    }

    @Test
    fun testArrayCompare() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "compare", false)
        println(results)
    }

    @Test
    fun testCastSlice() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE
            )
        )
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "castSlice", false)
        println(results)
    }

    @Test
    fun testSliceToArrayPointer() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "sliceToArrayPointer", false)
        println(results)
    }

    @Test
    fun testStdSort() {
        val machine =
            GoMachine(
                UMachineOptions(
                    listOf(PathSelectionStrategy.FORK_DEPTH),
                    timeout = Duration.INFINITE,
                    stepLimit = 10000u,
                    coverageZone = CoverageZone.TRANSITIVE
                )
            )
        val results = machine.analyzeAndResolve(Path.getProgram("call.go"), "stdSort", false)
        println(results)
    }

    @Test
    fun testRangeMap() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH)))
        val results = machine.analyzeAndResolve(Path.getProgram("loop.go"), "rangeMap", false)
        println(results)
    }

    @Test
    fun testRangeString() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH)))
        val results = machine.analyzeAndResolve(Path.getProgram("loop.go"), "rangeString", false)
        println(results)
    }

    @Test
    fun testReturnString() {
        val results = machine.analyzeAndResolve(Path.getProgram("playground.go"), "returnString", false)
        println(results)
    }

    @Test
    fun testSliceArray() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "sliceArray", false)
        println(results)
    }

    @Test
    fun testSliceArrayArgs() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "sliceArrayArgs", false)
        println(results)
    }

    @Test
    fun testSliceString() {
        val results = machine.analyzeAndResolve(Path.getProgram("slice.go"), "sliceString", false)
        println(results)
    }

    @Test
    fun testCallSwap() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE))
        val results = machine.analyzeAndResolve(Path.getProgram("call.go"), "callSwap", false)
        println(results)
    }
}
