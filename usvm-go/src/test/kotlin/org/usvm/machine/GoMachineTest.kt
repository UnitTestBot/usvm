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
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), coverageZone = CoverageZone.TRANSITIVE))
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
        val results = machine.analyzeAndResolve(Path.getProgram("sum_array.go"), "sumArray", false)
        println(results)
    }

    @Test
    fun testFirstArray() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.BFS),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
        )
        val results = machine.analyzeAndResolve(Path.getProgram("first_array.go"), "first", true)
        println(results)
    }
}
