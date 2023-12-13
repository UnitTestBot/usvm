package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import kotlin.time.Duration

class GoMachineTest {
    private val machine = GoMachine(UMachineOptions(listOf(PathSelectionStrategy.DFS), stopOnCoverage = -1))

    @Test
    fun testMax() {
        val results = machine.analyze("/home/buraindo/programs/max2.go", "max2", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testMin() {
        val results = machine.analyze("/home/buraindo/programs/min2.go", "min2", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testAdd() {
        val results = machine.analyze("/home/buraindo/programs/add.go", "add", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }

    @Test
    fun testLoopCrazy() {
        val results = machine.analyze("/home/buraindo/programs/loop_crazy.go", "loop", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
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
        val results = machine.analyze("/home/buraindo/programs/loop_infinite.go", "loop", false)
        println(results)
        println("Calls: ${machine.getCalls()}")
    }
}
