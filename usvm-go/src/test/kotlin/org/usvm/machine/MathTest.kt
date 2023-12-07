package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions

class MathTest {
    private val machine = GoMachine(UMachineOptions(stopOnCoverage = -1))

    @Test
    fun testMax() {
        val results = machine.analyze("/home/buraindo/programs/max2.go", "max2", false)
        println(results)
    }

    @Test
    fun testMin() {
        val results = machine.analyze("/home/buraindo/programs/min2.go", "min2", false)
        println(results)
    }
}
