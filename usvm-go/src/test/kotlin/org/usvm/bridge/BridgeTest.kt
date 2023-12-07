package org.usvm.bridge

import com.sun.jna.Structure
import org.junit.jupiter.api.Test

class BridgeTest {
    @Test
    fun testBridgePlayground() {
        val bridge = GoBridge()
        bridge.hello()
        bridge.inc()
        bridge.inc()
        println(bridge.getCalls())
        bridge.inc()
        bridge.inc()
        bridge.inc()
        println(bridge.getCalls())
        bridge.interpreter()
        println(bridge.initialize("/home/buraindo/programs/max2.go", "main", true))
        bridge.interpreter()
        println(GoBridge.getNumber())
        println(bridge.talk())
        println(bridge.talk())
        println(bridge.talk())
        println(GoBridge.getNumber())
        println(bridge.talk())
        println(bridge.talk())
        println(GoBridge.getNumber())
        val pointer = bridge.getBridge()
        println("java pointer: $pointer")
        println(bridge.getBridgeCalls(pointer))
        val main = bridge.getMain()
        val mainPointer = bridge.getMainPointer()
        println("pointer: $mainPointer")
        val name = bridge.getMethodName(mainPointer)
        println("name: $name")
        println("count: ${bridge.countStatementsOf(mainPointer)}")
        val statements = bridge.statementsOf(main)
        statements.forEach { println(it) }
        val methods = bridge.methods().toArray(2)
        println(methods)
        methods.forEach { println(it as Method) }
        val slice = bridge.slice()
        println(slice)
        println(slice.length)
        val methodsSliceInstance = Structure.newInstance(Method.ByReference::class.java, slice.data)
        methodsSliceInstance.read()
        val methodsSlice = methodsSliceInstance.toArray(slice.length)
        methodsSlice.forEach { println(it as Method) }
        val entryPoints = bridge.entryPoints(main)
        entryPoints.forEach { println(it) }
    }

    @Test
    fun testBridgeApplicationGraph() {
        val bridge = GoBridge()
        bridge.initialize("/home/buraindo/programs/max2.go", "main", true)
        val main = bridge.getMain()

        val entryPoints = bridge.entryPoints(main)
        entryPoints.forEach {
            println("entry point: $it")
            val successors = bridge.successors(it)
            println("successors: ${successors.joinToString(", ") { s -> s.toString() }}")
        }

        val slice = bridge.slice()
        val sliceInstance = Structure.newInstance(Method.ByReference::class.java, slice.data)
        sliceInstance.read()
        val methodsArray = sliceInstance.toArray(slice.length)
        methodsArray.forEach { println(it as Method) }

        val methodsSlice = bridge.methodsSlice()
        methodsSlice.forEach { println(it) }

        val statements = bridge.statementsOf(main)
        statements.forEach { println(it) }

        println(bridge.methodOf(statements[0]))
    }

    @Test
    fun testBridgeTypeSystem() {
        val bridge = GoBridge()
        bridge.initialize("/home/buraindo/programs/max2.go", "main", true)
        val any = bridge.getAnyType()
        println(any)
        println(bridge.isSupertype(any, any))
        println(bridge.hasCommonSubtype(any, listOf(any, any)))
    }

    @Test
    fun testBridgeCallback() {
        val bridge = GoBridge()
        bridge.callJavaMethod(Logger(), Logger::class.java)
    }
}

class Logger : Reference() {
    fun printHello() {
        println("logger callback worked")
    }
}