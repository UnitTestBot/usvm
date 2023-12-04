package org.usvm.bridge

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.Structure

interface Bridge : Library {
    // ------------ region: init
    fun initialize(filename: GoString): Result
    // ------------ region: init

    // ------------ region: machine
    fun getMain(): Method.ByValue
    fun getMethod(name: GoString): Method.ByValue
    // ------------ region: machine

    // ------------ region: application graph
    fun predecessors(inst: InstPointer): Slice
    fun successors(inst: InstPointer): Slice
    fun callees(inst: InstPointer): Slice
    fun callers(method: MethodPointer): Slice
    fun entryPoints(method: MethodPointer): Slice
    fun exitPoints(method: MethodPointer): Slice
    fun methodOf(inst: InstPointer): Method.ByValue
    fun statementsOf(method: MethodPointer): Slice
    // ------------ region: application graph

    // ------------ region: type system
    fun getAnyType(): Type.ByValue
    fun findSubTypes(type: TypePointer): Slice
    fun isInstantiable(type: TypePointer): Boolean
    fun isFinal(type: TypePointer): Boolean
    fun hasCommonSubtype(type: TypePointer, types: GoSlice): Boolean
    fun isSupertype(supertype: TypePointer, type: TypePointer): Boolean
    // ------------ region: type system

    // ------------ region: misc
    fun methodInfo(method: MethodPointer): MethodInfo.ByValue
    // ------------ region: misc

    // ------------ region: test
    fun talk(): Result
    fun getCalls(): Int
    fun inc()
    fun interpreter(): Interpreter
    fun hello()
    fun getBridge(): AnyPointer
    fun getBridgeCalls(bridge: AnyPointer): Int
    fun getMainPointer(): MethodPointer
    fun getMethodName(method: MethodPointer): GoString
    fun countStatementsOf(method: MethodPointer): Int
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

@Suppress("unused")
@Structure.FieldOrder("data", "len", "cap")
open class GoSlice(
    @JvmField var data: Pointer = Pointer.NULL,
    @JvmField var len: Long = 0,
    @JvmField var cap: Long = 0,
) : Structure(), Structure.ByValue

@Structure.FieldOrder("data", "length")
open class Slice(
    @JvmField var data: Pointer = Pointer.createConstant(0),
    @JvmField var length: Int = 0
) : Structure(), Structure.ByValue

@Structure.FieldOrder("message", "code")
open class Result(
    @JvmField var message: String = "",
    @JvmField var code: Int = 0
) : Structure(), Structure.ByValue {
    override fun toString(): String {
        return "$message: (code: $code)"
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

@Structure.FieldOrder("parameters", "localsCount")
open class MethodInfo(
    @JvmField var parameters: Slice = Slice(),
    @JvmField var localsCount: Int = 0,
) : Structure() {
    class ByValue : MethodInfo(), Structure.ByValue
}

typealias TypePointer = Long

@Structure.FieldOrder("pointer", "name")
open class Type(
    @JvmField var pointer: TypePointer = 0,
    @JvmField var name: String = "",
) : Structure() {
    override fun toString(): String {
        return "type: $name, pointer: $pointer"
    }

    class ByValue : Type(), Structure.ByValue
    class ByReference : Type(), Structure.ByReference
}