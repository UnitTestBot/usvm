package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions

class MathTest {
    private val machine = GoMachine(UMachineOptions(stopOnCoverage = -1))

    @Test
    fun testMax() {
        val results = machine.analyze("main")
        println(results)
    }
}
