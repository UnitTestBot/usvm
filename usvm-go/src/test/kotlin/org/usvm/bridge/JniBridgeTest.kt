package org.usvm.bridge

import org.junit.jupiter.api.Test
import org.usvm.util.Path
import kotlin.system.measureTimeMillis

class JniBridgeTest {
    @Test
    fun testBridgePlayground() {
        val bridge = GoJniBridge()
        bridge.initialize(Path.getProgram("add.go"), "add", false)
        println(bridge.getJniNumber())
        println(bridge.getMain())
        println(bridge.getMethod("add"))
    }

    @Test
    fun testBridgeApplicationGraph() {
        val bridge = GoJniBridge()
        bridge.initialize(Path.getProgram("max2.go"), "main", true)
        val main = bridge.getMain()

        val entryPoints = bridge.entryPoints(main)
        entryPoints.forEach {
            println("entry point: $it")
            val successors = bridge.successors(it)
            println("successors: ${successors.joinToString(", ") { s -> s.toString() }}")
        }

        val statements = bridge.statementsOf(main)
        statements.forEach { println(it) }

        println(bridge.methodOf(statements[0]))
    }

    @Test
    fun testBridgeTypeSystem() {
        val bridge = GoJniBridge()
        bridge.initialize(Path.getProgram("max2.go"), "main", true)
        val any = bridge.getAnyType()
        println(any)
        println(bridge.isSupertype(any, any))
        println(bridge.hasCommonSubtype(any, listOf(any, any)))
    }

    @Test
    fun testBridgeBenchmarkString() {
        val bridge = GoJniBridge()

        for (i in 0 until 1000000) {
            bridge.getJniNumber()
        }
        val time1 = measureTimeMillis {
            for (i in 0 until 10000000) {
                bridge.initialize("file_$i", "xxx", false)
            }
        }
        val time2 = measureTimeMillis {
            for (i in 0 until 10000000) {
                bridge.initialize2("file_$i", "xxx", false)
            }
        }
        println("$time1 $time2")
    }
}