package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.bridge.BridgeType
import org.usvm.util.Path
import kotlin.time.Duration

class GoMachineTest {
    private val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)), BridgeType.JNA)

    @Test
    fun testMax() {
        val results = machine.analyze(Path.getProgram("max2.go"), "max2", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testMaxJni() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)), BridgeType.JNI)
        val results = machine.analyze(Path.getProgram("max2.go"), "max2", false)
        println(results)
    }

    @Test
    fun testMaxNalim() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)), BridgeType.NALIM)
        val results = machine.analyze(Path.getProgram("max2.go"), "max2", false)
        println(results)
    }

    @Test
    fun testMin() {
        val results = machine.analyze(Path.getProgram("min2.go"), "min2", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testMinJni() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)), BridgeType.JNI)
        val results = machine.analyze(Path.getProgram("min2.go"), "min2", false)
        println(results)
    }

    @Test
    fun testAdd() {
        val results = machine.analyze(Path.getProgram("add.go"), "add", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testAddJni() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)), BridgeType.JNI)
        val results = machine.analyze(Path.getProgram("add.go"), "add", false)
        println(results)
    }

    @Test
    fun testLoop() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.BFS)), BridgeType.JNA)
        val results = machine.analyze(Path.getProgram("loop.go"), "loop", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testLoopJni() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.BFS)), BridgeType.JNI)
        val results = machine.analyze(Path.getProgram("loop.go"), "loop", true)
        println(results)
    }

    @Test
    fun testLoopHard() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.BFS)), BridgeType.JNA)
        val results = machine.analyze(Path.getProgram("loop_hard.go"), "loop", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testLoopHardJni() {
        val machine = GoMachine(
            UMachineOptions(listOf(PathSelectionStrategy.BFS), timeout = Duration.INFINITE),
            BridgeType.JNI,
        )
        val results = machine.analyze(Path.getProgram("loop_hard.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopHardNalim() {
        val machine = GoMachine(
            UMachineOptions(listOf(PathSelectionStrategy.BFS), timeout = Duration.INFINITE),
            BridgeType.NALIM,
        )
        val results = machine.analyze(Path.getProgram("loop_hard.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopCrazy() {
        val results = machine.analyze(Path.getProgram("loop_crazy.go"), "loop", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testLoopCrazyJni() {
        val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS)), BridgeType.JNI)
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
            BridgeType.JNA
        )
        val results = machine.analyze(Path.getProgram("loop_infinite.go"), "loop", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testLoopInfiniteJni() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.DFS),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
            BridgeType.JNI
        )
        val results = machine.analyze(Path.getProgram("loop_infinite.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopInfiniteNalim() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.DFS),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
            BridgeType.NALIM
        )
        val results = machine.analyze(Path.getProgram("loop_infinite.go"), "loop", false)
        println(results)
    }

    @Test
    fun testLoopCollatzNalim() {
        val machine = GoMachine(
            UMachineOptions(
                listOf(PathSelectionStrategy.FORK_DEPTH),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
            BridgeType.NALIM
        )
        val results = machine.analyze(Path.getProgram("loop_collatz.go"), "loop", false)
        println(results)
    }
}
