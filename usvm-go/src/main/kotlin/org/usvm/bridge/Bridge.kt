package org.usvm.bridge

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure

class Bridge {
    private val path = "/home/buraindo/libs/java_bridge.so"
    private val bridge = Native.load(path, GoBridge::class.java)

    init {
        System.load(path)
    }

    fun initialize(filename: String): String { //TODO proper error handling
        return bridge.initialize(GoString.ByValue(filename)).toString()
    }

    fun talk(): String { //TODO proper error handling
        return bridge.talk().toString()
    }

    fun getCalls(): Int {
        return bridge.getCalls()
    }

    fun inc() {
        bridge.inc()
    }

    fun interpreter() {
        println(bridge.interpreter())
    }

    fun hello() {
        bridge.hello()
    }

    companion object {
        private var i: Int = 0

        @JvmStatic
        fun increase() {
            println("INCREASE")
            i++
        }

        @JvmStatic
        fun getNumber(): Int {
            return i
        }
    }
}

interface GoBridge : Library {
    fun initialize(filename: GoString.ByValue): Error.ByValue
    fun talk(): Error.ByValue
    fun getCalls(): Int
    fun inc()
    fun interpreter(): Interpreter.ByValue
    fun hello()
}

@Structure.FieldOrder("message", "code")
open class Error(
    @JvmField var message: String = "",
    @JvmField var code: Int = 0
) : Structure() {
    override fun toString(): String {
        return "error (code: $code): $message"
    }

    class ByValue : Error(), Structure.ByValue
}

@Structure.FieldOrder("name")
open class Interpreter(
    @JvmField var name: String = ""
) : Structure() {
    override fun toString(): String {
        return "interpreter: $name"
    }

    class ByValue : Interpreter(), Structure.ByValue
}

@Structure.FieldOrder("p", "n")
open class GoString(
    @JvmField var p: String = "",
    @JvmField var n: Int = p.length
) : Structure() {
    class ByValue(p: String) : GoString(p), Structure.ByValue
}
