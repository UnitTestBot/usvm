package org.usvm.bridge

import one.nalim.Link
import one.nalim.Linker
import org.usvm.util.Path

typealias InstPointer = Long
typealias MethodPointer = Long
typealias TypePointer = Long

object Bridge {
    private val path = Path.getLib("java_bridge")

    init {
        Linker.loadLibrary(path)
        Linker.linkClass(Bridge::class.java)
    }

    // ------------ region: initialize
    @JvmStatic
    @Link
    external fun initialize(
        fileBytes: ByteArray,
        fileSize: Int,
        debug: Boolean
    ): Int
    // ------------ region: initialize

    // ------------ region: shutdown
    @JvmStatic
    @Link
    external fun shutdown(): Int
    // ------------ region: shutdown

    // ------------ region: machine
    @JvmStatic
    @Link
    external fun getMethod(name: ByteArray, len: Int): MethodPointer
    // ------------ region: machine

    // ------------ region: application graph
    @JvmStatic
    @Link
    external fun predecessors(inst: InstPointer, instructions: LongArray, len: Int): Int

    @JvmStatic
    @Link
    external fun successors(inst: InstPointer, instructions: LongArray, len: Int): Int

    @JvmStatic
    @Link
    external fun callees(inst: InstPointer, methods: LongArray)

    @JvmStatic
    @Link
    external fun callers(method: MethodPointer, instructions: LongArray, len: Int): Int

    @JvmStatic
    @Link
    external fun entryPoints(method: MethodPointer, instructions: LongArray)

    @JvmStatic
    @Link
    external fun methodImplementation(method: MethodPointer, type: TypePointer): MethodPointer

    @JvmStatic
    @Link
    external fun exitPoints(method: MethodPointer, instructions: LongArray, len: Int): Int

    @JvmStatic
    @Link
    external fun methodOf(inst: InstPointer): MethodPointer

    @JvmStatic
    @Link
    external fun statementsOf(method: MethodPointer, instructions: LongArray, len: Int): Int
    // ------------ region: application graph

    // ------------ region: type system
    @JvmStatic
    @Link
    external fun getAnyType(): TypePointer

    @JvmStatic
    @Link
    external fun getStringType(): TypePointer

    @JvmStatic
    @Link
    external fun findSubTypes(type: TypePointer, types: LongArray, len: Int): Int

    @JvmStatic
    @Link
    external fun isInstantiable(type: TypePointer): Boolean

    @JvmStatic
    @Link
    external fun isFinal(type: TypePointer): Boolean

    @JvmStatic
    @Link
    external fun hasCommonSubtype(type: TypePointer, types: LongArray, len: Int): Boolean

    @JvmStatic
    @Link
    external fun isSupertype(supertype: TypePointer, type: TypePointer): Boolean

    @JvmStatic
    @Link
    external fun typeToSort(type: TypePointer): Byte

    @JvmStatic
    @Link
    external fun arrayElementType(type: TypePointer): TypePointer

    @JvmStatic
    @Link
    external fun mapKeyValueTypes(type: TypePointer, args: Long)

    @JvmStatic
    @Link
    external fun structFieldTypes(type: TypePointer, args: Long)

    @JvmStatic
    @Link
    external fun tupleTypes(type: TypePointer, args: Long)

    @JvmStatic
    @Link
    external fun typeHash(type: TypePointer): Long
    // ------------ region: type system

    // ------------ region: interpreter
    @JvmStatic
    @Link
    external fun methodInfo(method: MethodPointer, args: Long)
    @JvmStatic
    @Link
    external fun instInfo(inst: InstPointer, args: Long)
    // ------------ region: interpreter

    // ------------ region: api
    @JvmStatic
    @Link
    external fun start(): Int

    @JvmStatic
    @Link
    external fun step(inst: InstPointer, lastBlock: Int, args: Long): InstPointer
    // ------------ region: api
}