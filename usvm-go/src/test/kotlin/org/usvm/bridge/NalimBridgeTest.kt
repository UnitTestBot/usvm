package org.usvm.bridge

import org.junit.jupiter.api.Test
import org.usvm.util.Path


class NalimBridgeTest {
    @Test
    fun testBridgePlayground() {
        println(NalimBridge.getNumber())

        val arr = IntArray(5)
        for (i in arr) {
            print("$i ")
        }
        println()
        NalimBridge.getNumbers(arr, 5)
        for (i in 0 until 5) {
            print("${arr[i]} ")
        }
        println()
    }

    @Test
    fun testBridgeApplicationGraph() {
        val bridge = GoNalimBridge()
        println(bridge.initialize(Path.getProgram("max2.go"), "max2", false))

        val method = bridge.getMethod("max2")
        print("method: ")
        println(method)
        val statements = bridge.statementsOf(method)
        print("statements: ")
        for (i in statements) {
            print("$i ")
        }
        println()
        val predecessors = bridge.predecessors(statements[2])
        print("predecessors: ")
        for (i in predecessors) {
            print("$i ")
        }
        println()
        val successors = bridge.successors(statements[0])
        print("successors: ")
        for (i in successors) {
            print("$i ")
        }
        println()
        val callees = bridge.callees(statements[0])
        print("callees: ")
        for (i in callees) {
            print("$i ")
        }
        println()
        val callers = bridge.callers(method)
        print("callers: ")
        for (i in callers) {
            print("$i ")
        }
        println()
        val entryPoints = bridge.entryPoints(method)
        print("entryPoints: ")
        for (i in entryPoints) {
            print("$i ")
        }
        println()
        val exitPoints = bridge.exitPoints(method)
        print("exitPoints: ")
        for (i in exitPoints) {
            print("$i ")
        }
        println()
        val methodOf = bridge.methodOf(statements[3])
        print("methodOf: ")
        println(methodOf)
    }

    @Test
    fun testBridgeTypeSystem() {
        val bridge = GoNalimBridge()
        println(bridge.initialize(Path.getProgram("max2.go"), "max2", false))
        val any = bridge.getAnyType()
        println(any)
        println(bridge.isSupertype(any, any))
        println(bridge.hasCommonSubtype(any, listOf(any, any)))
        println(bridge.isFinal(any))
        println(bridge.isInstantiable(any))
    }
}