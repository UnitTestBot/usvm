package org.usvm.concrete.interpreter

import org.junit.jupiter.api.Test
import org.usvm.concrete.Runner
import org.usvm.sample.AbsProgram
import kotlin.test.assertEquals

class MathTests {
    val program = AbsProgram()
    val runner = Runner(program)

    @Test
    fun abs() {
        val method = AbsProgram.m1
        val results = runner.run(method)
        assertEquals(2, results.size)
        println(results)
    }

    @Test
    fun testik() {
        val method = AbsProgram.m2
        val results = runner.run(method)
        assertEquals(2, results.size)
        println(results.joinToString("\n"))
    }

    @Test
    fun testDivBy0() {
        val method = AbsProgram.m3
        val results = runner.run(method)
        assertEquals(2, results.size)
        println(results.joinToString("\n"))
    }

    @Test
    fun testOutOfBounds() {
        val method = AbsProgram.m4
        val results = runner.run(method)
        assertEquals(3, results.size)
        println(results.joinToString("\n"))
    }
}