package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.callgraph.CallGraphTestClass1
import org.usvm.samples.callgraph.CallGraphTestClass2
import org.usvm.samples.callgraph.CallGraphTestClass3
import org.usvm.samples.callgraph.CallGraphTestClass4
import org.usvm.util.getJcMethodByName
import kotlin.test.assertTrue

class JcCallGraphStatisticsTests : JavaMethodTestRunner() {

    private val appGraph = JcApplicationGraph(cp)
    private val typeStream = JcTypeSystem(cp, options.typeOperationsTimeout).topTypeStream()
    private val statistics = JcCallGraphStatistics(5u, appGraph, typeStream, 100)

    @Test
    fun `base method is reachable`() {
        val methodFrom = cp.getJcMethodByName(CallGraphTestClass3::C)
        val methodTo = cp.getJcMethodByName(CallGraphTestClass1::A)
        assertTrue { statistics.checkReachability(methodFrom, methodTo) }
    }

    @Test
    fun `method override is reachable`() {
        val methodFrom = cp.getJcMethodByName(CallGraphTestClass3::C)
        val methodTo = cp.getJcMethodByName(CallGraphTestClass2::A)
        assertTrue { statistics.checkReachability(methodFrom, methodTo) }
    }

    @Test
    fun `interface implementation is reachable`() {
        val methodFrom = cp.getJcMethodByName(CallGraphTestClass3::D)
        val methodTo = cp.getJcMethodByName(CallGraphTestClass4::A)
        assertTrue { statistics.checkReachability(methodFrom, methodTo) }
    }

    @Test
    fun `final method is reachable`() {
        val methodFrom = cp.getJcMethodByName(CallGraphTestClass3::E)
        val methodTo = cp.getJcMethodByName(CallGraphTestClass1::B)
        assertTrue { statistics.checkReachability(methodFrom, methodTo) }
    }

    // CallGraphTestClass3::C -> CallGraphTestClass2::A -> CallGraphTestClass4::A
    @Test
    fun `transitive reachability test`() {
        val methodFrom = cp.getJcMethodByName(CallGraphTestClass3::C)
        val methodTo = cp.getJcMethodByName(CallGraphTestClass4::A)
        assertTrue { statistics.checkReachability(methodFrom, methodTo) }
    }
}
