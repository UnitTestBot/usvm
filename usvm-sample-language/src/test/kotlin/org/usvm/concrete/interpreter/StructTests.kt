package org.usvm.concrete.interpreter

import org.junit.jupiter.api.Test
import org.usvm.concrete.Runner
import org.usvm.sample.StructProgram

class StructTests {
    val program = StructProgram()
    val runner = Runner(program)

    @Test
    fun structTestik() {
        val results = runner.run(StructProgram.method3)
        println(results.joinToString("\n"))
    }
}