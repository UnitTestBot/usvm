package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.util.Path
import kotlin.time.Duration

class GoMachineTest {
    private val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)))

    @Test
    fun testMax() {
        val results = machine.analyze(Path.getProgram("max2.go"), "max2", false)
        println(results)
    }

    @Test
    fun testMin() {
        val results = machine.analyze(Path.getProgram("min2.go"), "min2", false)
        println(results)
    }

    @Test
    fun testAdd() {
        val results = machine.analyze(Path.getProgram("add.go"), "add", false)
        println(results)
    }

    @Test
    fun testGcd() {
        val results = machine.analyze(Path.getProgram("gcd.go"), "gcd", false)
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
        val results = machine.analyze(Path.getProgram("loop_collatz.go"), "loop", false)
        println(results)
    }
}
