package org.usvm.bridge

import org.junit.jupiter.api.Test
import org.usvm.util.Path


class BridgeTest {
    @Test
    fun testBridgeApplicationGraph() {
        val bridge = GoBridge()
        println(bridge.initialize(Path.getProgram("max2.go"), false))

        val method = bridge.getMethod("max2")
        print("method: ")
        println(method)
        val statements = bridge.statementsOf(method).first
        print("statements: ")
        for (i in statements) {
            print("$i ")
        }
        println()
        val predecessors = bridge.predecessors(statements[2]).first
        print("predecessors: ")
        for (i in predecessors) {
            print("$i ")
        }
        println()
        val successors = bridge.successors(statements[0]).first
        print("successors: ")
        for (i in successors) {
            print("$i ")
        }
        println()
        val callees = bridge.callees(statements[0]).first
        print("callees: ")
        for (i in callees) {
            print("$i ")
        }
        println()
        val callers = bridge.callers(method).first
        print("callers: ")
        for (i in callers) {
            print("$i ")
        }
        println()
        val entryPoints = bridge.entryPoints(method).first
        print("entryPoints: ")
        for (i in entryPoints) {
            print("$i ")
        }
        println()
        val exitPoints = bridge.exitPoints(method).first
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
        val bridge = GoBridge()
        println(bridge.initialize(Path.getProgram("max2.go"), false))
        val any = bridge.getAnyType()
        println(any)
        println(bridge.isSupertype(any, any))
        println(bridge.hasCommonSubtype(any, listOf(any, any)))
        println(bridge.isFinal(any))
        println(bridge.isInstantiable(any))
    }
}