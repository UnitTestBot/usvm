package org.usvm.concrete.interpreter

import org.junit.jupiter.api.Test
import org.usvm.concrete.Runner
import org.usvm.sample.DfsProgram

class DfsTests {
    val runner = Runner(DfsProgram(), 20)
    @Test
    fun runDfs() {
        val states = runner.run(DfsProgram.dfs)
        println(states.joinToString("\n"))
    }

    @Test
    fun runDfsBamboo() {
        val states = runner.run(DfsProgram.calcSumBamboo)
        println(states.joinToString("\n"))
    }
}