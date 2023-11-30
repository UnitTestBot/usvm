package org.usvm.bridge

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.Structure

interface Bridge : Library {
    // ------------ region: init
    fun initialize(filename: GoString): Error
    // ------------ region: init

    // ------------ region: machine
    fun getMain(): Method.ByValue
    // ------------ region: machine

    // ------------ region: application graph
    fun predecessors(pointer: InstPointer): Slice
    fun successors(pointer: InstPointer): Slice
    fun callees(pointer: InstPointer): Slice
    fun callers(pointer: MethodPointer): Slice
    fun entryPoints(pointer: MethodPointer): Slice
    fun exitPoints(pointer: MethodPointer): Slice
    fun methodOf(pointer: InstPointer): Method
    fun statementsOf(pointer: MethodPointer): Slice
    // ------------ region: application graph

    // ------------ region: test
    fun talk(): Error
    fun getCalls(): Int
    fun inc()
    fun interpreter(): Interpreter
    fun hello()
    fun getBridge(): AnyPointer
    fun getBridgeCalls(pointer: AnyPointer): Int
    fun getMainPointer(): MethodPointer
    fun getMethodName(pointer: MethodPointer): GoString
    fun countStatementsOf(pointer: MethodPointer): Int
    fun methods(): Method.ByReference
    fun slice(): Slice
    fun methodsSlice(): Slice
    // ------------ region: test
}

typealias AnyPointer = Long

@Structure.FieldOrder("p", "n")
open class GoString(
    @JvmField var p: String = "",
    @JvmField var n: Int = p.length,
) : Structure(), Structure.ByValue

@Structure.FieldOrder("data", "length")
open class Slice(
    @JvmField var data: Pointer = Pointer.createConstant(0),
    @JvmField var length: Int = 0
) : Structure(), Structure.ByValue

@Structure.FieldOrder("message", "code")
open class Error(
    @JvmField var message: String = "",
    @JvmField var code: Int = 0
) : Structure(), Structure.ByValue {
    override fun toString(): String {
        return "error (code: $code): $message"
    }
}

@Structure.FieldOrder("name")
open class Interpreter(
    @JvmField var name: String = ""
) : Structure(), Structure.ByValue {
    override fun toString(): String {
        return "interpreter: $name"
    }
}

typealias InstPointer = Long

@Structure.FieldOrder("pointer", "statement")
open class Inst(
    @JvmField var pointer: InstPointer = 0,
    @JvmField var statement: String = "",
) : Structure() {
    override fun toString(): String {
        return "statement: $statement, pointer: $pointer"
    }

    class ByValue : Inst(), Structure.ByValue
    class ByReference : Inst(), Structure.ByReference
}

typealias MethodPointer = Long

@Structure.FieldOrder("pointer", "name")
open class Method(
    @JvmField var pointer: MethodPointer = 0,
    @JvmField var name: String = "",
) : Structure() {
    override fun toString(): String {
        return "method: $name, pointer: $pointer"
    }

    class ByValue : Method(), Structure.ByValue
    class ByReference : Method(), Structure.ByReference
}