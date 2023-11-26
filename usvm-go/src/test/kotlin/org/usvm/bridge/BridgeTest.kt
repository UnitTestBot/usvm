package org.usvm.bridge

import org.junit.jupiter.api.Test

class BridgeTest {
    @Test
    fun testBridge() {
        val bridge = Bridge()
        bridge.hello()
        bridge.inc()
        bridge.inc()
        println(bridge.getCalls())
        bridge.inc()
        bridge.inc()
        bridge.inc()
        println(bridge.getCalls())
        bridge.interpreter()
        println(bridge.initialize("/home/buraindo/programs/max2.go"))
        bridge.interpreter()
        println(Bridge.getNumber())
        println(bridge.talk())
        println(bridge.talk())
        println(bridge.talk())
        println(Bridge.getNumber())
        println(bridge.talk())
        println(bridge.talk())
        println(Bridge.getNumber())
    }
}