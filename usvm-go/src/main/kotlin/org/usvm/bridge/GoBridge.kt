package org.usvm.bridge

import org.usvm.api.Api
import org.usvm.machine.GoMethod
import org.usvm.machine.GoMethodInfo
import org.usvm.machine.GoInst
import org.usvm.machine.GoInstInfo
import org.usvm.machine.GoType
import org.usvm.machine.type.Type
import java.nio.Buffer
import java.nio.ByteBuffer

class GoBridge {
    private val objects = LongArray(BUF_SIZE)
    private val single = LongArray(1)

    private val buf: ByteBuffer = ByteBuffer.allocateDirect(BUF_SIZE)
    private val address: Long

    init {
        val fieldAddress = Buffer::class.java.getDeclaredField("address")
        fieldAddress.setAccessible(true)
        address = fieldAddress.getLong(buf)
    }

    // ------------ region: initialize

    fun initialize(file: String, debug: Boolean): Int {
        return Bridge.initialize(file.toByteArray(), file.length, debug)
    }

    // ------------ region: initialize

    // ------------ region: shutdown

    fun shutdown(): Int {
        return Bridge.shutdown()
    }

    // ------------ region: shutdown

    // ------------ region: machine

    fun getMethod(name: String) = Bridge.getMethod(name.toByteArray(), name.length)

    // ------------ region: machine

    // ------------ region: application graph

    fun predecessors(inst: GoInst): Pair<LongArray, Int> = toArray { arr, len ->
        Bridge.predecessors(inst, arr, len)
    }

    fun successors(inst: GoInst): Pair<LongArray, Int> = toArray { arr, len ->
        Bridge.successors(inst, arr, len)
    }

    fun callees(inst: GoInst): Pair<LongArray, Int> = toArraySingle { arr ->
        Bridge.callees(inst, arr)
    }

    fun callers(method: GoMethod): Pair<LongArray, Int> = toArray { arr, len ->
        Bridge.callers(method, arr, len)
    }

    fun entryPoints(method: GoMethod): Pair<LongArray, Int> = toArraySingle { arr ->
        Bridge.entryPoints(method, arr)
    }

    fun exitPoints(method: GoMethod): Pair<LongArray, Int> = toArray { arr, len ->
        Bridge.exitPoints(method, arr, len)
    }

    fun methodOf(inst: GoInst): GoMethod = Bridge.methodOf(inst)

    fun statementsOf(method: GoMethod): Pair<LongArray, Int> = toArray { arr, len ->
        Bridge.statementsOf(method, arr, len)
    }

    // ------------ region: application graph

    // ------------ region: type system

    fun getAnyType(): GoType = Bridge.getAnyType()

    fun findSubTypes(type: GoType): Pair<LongArray, Int> = toArray { arr, len ->
        Bridge.findSubTypes(type, arr, len)
    }

    fun isInstantiable(type: GoType): Boolean = Bridge.isInstantiable(type)

    fun isFinal(type: GoType): Boolean = Bridge.isFinal(type)

    fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        return Bridge.hasCommonSubtype(type, types.map { it }.toLongArray(), types.size)
    }

    fun isSupertype(supertype: GoType, type: GoType): Boolean {
        return Bridge.isSupertype(supertype, type)
    }

    // ------------ region: type system

    // ------------ region: interpreter

    fun methodInfo(method: GoMethod): GoMethodInfo {
        Bridge.methodInfo(method, address)
        val returnType = buf.get()
        val variablesCount = buf.int
        val allocationsCount = buf.int
        val parametersCount = buf.int
        val parametersTypes = Array(parametersCount) { Type.valueOf(buf.get()) }
        buf.rewind()
        return GoMethodInfo(
            Type.valueOf(returnType),
            variablesCount,
            allocationsCount,
            parametersCount,
            parametersTypes
        )
    }

    fun instInfo(inst: GoInst): GoInstInfo {
        Bridge.instInfo(inst, address)
        val length = buf.int
        val bytes = ByteArray(length) { buf.get() }
        buf.rewind()
        return GoInstInfo(String(bytes))
    }

    // ------------ region: interpreter

    // ------------ region: api

    fun start(): Int = Bridge.start()

    fun step(api: Api, inst: GoInst): GoInst {
        val nextInst = Bridge.step(inst, api.getLastBlock(), address).let { api.mk(buf, it) }
        buf.rewind()
        return nextInst
    }

    // ------------ region: api

    // ------------ region: utils

    private fun toArray(op: (LongArray, Int) -> Int): Pair<LongArray, Int> {
        val len = op(objects, BUF_SIZE)
        return objects to len
    }

    private fun toArraySingle(op: (LongArray) -> Unit): Pair<LongArray, Int> {
        op(single)
        return single to 1
    }

    // ------------ region: utils

    companion object {
        private const val BUF_SIZE = 1 shl 16
    }
}
