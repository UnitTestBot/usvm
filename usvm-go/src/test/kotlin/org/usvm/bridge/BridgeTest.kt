package org.usvm.bridge

import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class BridgeTest {
    private val jni = GoJniBridge()
    private val jna = GoJnaBridge()
    private val nalim = GoNalimBridge()

    @Test
    fun testBridgeBenchmark() {
        val time1 = measureTimeMillis {
            for (i in 0 until 100000000) {
                jni.getNumber()
            }
        }
        val time2 = measureTimeMillis {
            for (i in 0 until 100000000) {
                jna.getNumber()
            }
        }
        val time3 = measureTimeMillis {
            for (i in 0 until 100000000) {
                nalim.getNumber()
            }
        }
        println("$time1 $time2 $time3")
    }
}