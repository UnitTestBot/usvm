package org.usvm.samples.buraindo

import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcMachine
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.util.getJcMethodByName

class MathTest: JavaMethodTestRunner() {
    private val machine = JcMachine(cp, UMachineOptions())

    @Test
    fun testMax() {
        val method = cp.getJcMethodByName(Max2::max2).method
        val results = machine.analyze(method, emptyList())
        println(results)
    }
}