package org.usvm.bridge

import org.usvm.api.Api
import org.usvm.machine.GoMethod
import org.usvm.machine.GoMethodInfo
import org.usvm.machine.GoInst
import org.usvm.machine.GoType

class GoBridge {
    private val mkArgs = ByteArray(BUF_SIZE)
    private val objects = LongArray(BUF_SIZE)
    private val methodInfo = IntArray(2)
    private val single = LongArray(1)

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
        Bridge.methodInfo(method, methodInfo)
        return GoMethodInfo(methodInfo[0], methodInfo[1])
    }

    // ------------ region: interpreter

    // ------------ region: api

    fun start(): Int = Bridge.start()

    fun step(api: Api, inst: GoInst): GoInst {
        val nextInst = Bridge.step(inst, api.getLastBlock(), mkArgs)
        api.mk(mkArgs, nextInst != 0L)
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
