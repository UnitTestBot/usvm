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
        val results = machine.analyzeAndResolve(Path.getProgram("max2.go"), "max2", false)
        println(results)
    }

    @Test
    fun testMin() {
        val results = machine.analyzeAndResolve(Path.getProgram("min2.go"), "min2", false)
        println(results)
    }

    @Test
    fun testMin3() {
        val machine =
            GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE))
        val results = machine.analyzeAndResolve(Path.getProgram("min3.go"), "min3", false)
        println(results)
    }

    @Test
    fun testAdd() {
        val results = machine.analyzeAndResolve(Path.getProgram("add.go"), "add", false)
        println(results)
    }

    @Test
    fun testGcd() {
        val results = machine.analyzeAndResolve(Path.getProgram("gcd.go"), "gcd", false)
        println(results)
    }

    @Test
    fun testLoop() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.BFS)))
        val results = machine.analyze(Path.getProgram("loop.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopHard() {
        val machine = GoMachine(
            UMachineOptions(listOf(PathSelectionStrategy.BFS), timeout = Duration.INFINITE),
        )
        val results = machine.analyze(Path.getProgram("loop_hard.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopCrazy() {
        val results = machine.analyze(Path.getProgram("loop_crazy.go"), "loop", false)
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
        val results = machine.analyze(Path.getProgram("loop_infinite.go"), "loop", false)
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
        val results = machine.analyzeAndResolve(Path.getProgram("loop_collatz.go"), "loop", false)
        println(results)
    }

    @Test
    fun testSumArray() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH)))
        val results = machine.analyzeAndResolve(Path.getProgram("sum_array.go"), "sumArray", false)
        println(results)
    }

    @Test
    fun testFirstArray() {
        val results = machine.analyzeAndResolve(Path.getProgram("first_array.go"), "first", false)
        println(results)
    }

    @Test
    fun testStruct() {
        val results = machine.analyzeAndResolve(Path.getProgram("struct.go"), "isOld", false)
        println(results)
    }

    @Test
    fun testStructFieldSet() {
        val results = machine.analyzeAndResolve(Path.getProgram("struct_field_set.go"), "setPerfectAge", false)
        println(results)
    }

    @Test
    fun testStructPointer() {
        val results = machine.analyzeAndResolve(Path.getProgram("struct_pointer.go"), "setPerfectAge", false)
        println(results)
    }

    @Test
    fun testMapLookup() {
        val results = machine.analyzeAndResolve(Path.getProgram("map_lookup.go"), "lookup", false)
        println(results)
    }

    @Test
    fun testMapUpdate() {
        val results = machine.analyzeAndResolve(Path.getProgram("map_update.go"), "update", false)
        println(results)
    }

    @Test
    fun testPointer() {
        val results = machine.analyzeAndResolve(Path.getProgram("pointer.go"), "pointer", false)
        println(results)
    }

    @Test
    fun testPointerArray() {
        val results = machine.analyzeAndResolve(Path.getProgram("pointer_array.go"), "pointer", false)
        println(results)
    }

    @Test
    fun testPanic() {
        val results = machine.analyzeAndResolve(Path.getProgram("panic.go"), "panicSimple", false)
        println(results)
    }
}
