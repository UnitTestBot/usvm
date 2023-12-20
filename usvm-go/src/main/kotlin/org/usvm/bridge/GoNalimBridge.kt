package org.usvm.bridge

import org.usvm.api.GoNalimApi
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoMethodInfo
import org.usvm.domain.GoType
import org.usvm.util.GoResult

class GoNalimBridge : Bridge {
    private val args = LongArray(100)

    private lateinit var api: GoNalimApi

    // ------------ region: initialize

    override fun initialize(file: String, entrypoint: String, debug: Boolean): GoResult {
        return GoResult(
            "initialize",
            NalimBridge.initialize(file.toByteArray(), file.length, entrypoint.toByteArray(), entrypoint.length, debug)
        )
    }

    // ------------ region: initialize

    // ------------ region: shutdown

    override fun shutdown(): GoResult {
        return GoResult("shutdown", NalimBridge.shutdown())
    }

    // ------------ region: shutdown

    // ------------ region: machine

    override fun getMain(): GoMethod = toMethod(NalimBridge.getMain())

    override fun getMethod(name: String) = toMethod(NalimBridge.getMethod(name.toByteArray(), name.length))

    // ------------ region: machine

    // ------------ region: application graph

    override fun predecessors(inst: GoInst): Array<GoInst> = toArray(100, ::toInst) { arr, len ->
        NalimBridge.predecessors(inst.pointer, arr, len)
    }

    override fun successors(inst: GoInst): Array<GoInst> = toArray(100, ::toInst) { arr, len ->
        NalimBridge.successors(inst.pointer, arr, len)
    }

    override fun callees(inst: GoInst): Array<GoMethod> = toArrayFixed(1, ::toMethod) { arr ->
        NalimBridge.callees(inst.pointer, arr)
    }

    override fun callers(method: GoMethod): Array<GoInst> = toArray(100, ::toInst) { arr, len ->
        NalimBridge.callers(method.pointer, arr, len)
    }

    override fun entryPoints(method: GoMethod): Array<GoInst> = toArrayFixed(1, ::toInst) { arr ->
        NalimBridge.entryPoints(method.pointer, arr)
    }

    override fun exitPoints(method: GoMethod): Array<GoInst> = toArray(100, ::toInst) { arr, len ->
        NalimBridge.exitPoints(method.pointer, arr, len)
    }

    override fun methodOf(inst: GoInst): GoMethod = toMethod(NalimBridge.methodOf(inst.pointer))

    override fun statementsOf(method: GoMethod): Array<GoInst> = toArray(100, ::toInst) { arr, len ->
        NalimBridge.statementsOf(method.pointer, arr, len)
    }

    // ------------ region: application graph

    // ------------ region: type system

    override fun getAnyType(): GoType = toType(NalimBridge.getAnyType())

    override fun findSubTypes(type: GoType): Array<GoType> = toArray(100, ::toType) { arr, len ->
        NalimBridge.findSubTypes(type.pointer, arr, len)
    }

    override fun isInstantiable(type: GoType): Boolean = NalimBridge.isInstantiable(type.pointer)

    override fun isFinal(type: GoType): Boolean = NalimBridge.isFinal(type.pointer)

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        return NalimBridge.hasCommonSubtype(type.pointer, types.map { it.pointer }.toLongArray(), types.size)
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean {
        return NalimBridge.isSupertype(supertype.pointer, type.pointer)
    }

    // ------------ region: type system

    // ------------ region: interpreter

    override fun methodInfo(method: GoMethod): GoMethodInfo {
        val array = IntArray(2)
        NalimBridge.methodInfo(method.pointer, array)
        return GoMethodInfo(array[0], array[1])
    }

    // ------------ region: interpreter

    // ------------ region: api

    fun withApi(api: GoNalimApi): GoNalimBridge = this.also { it.api = api }

    override fun start(): Int = NalimBridge.start()

    override fun step(inst: GoInst): GoInst {
        val nextInst = NalimBridge.step(inst.pointer, api.getLastBlock(), args)

        val index = api.mk(args)
        if (nextInst != 0L) {
            api.setLastBlock(args[index].toInt())
        }

        return toInst(nextInst)
    }

    // ------------ region: api

    // ------------ region: utils

    private inline fun <reified R> toArray(
        size: Int,
        mapper: (Long) -> (R),
        op: (LongArray, Int) -> Int
    ): Array<R> {
        val out = LongArray(size)
        val len = op(out, size)
        return Array(len) { mapper(out[it]) }
    }

    private inline fun <reified R> toArrayFixed(
        size: Int,
        mapper: (Long) -> (R),
        op: (LongArray) -> Unit
    ): Array<R> {
        val out = LongArray(size)
        op(out)
        return Array(size) { mapper(out[it]) }
    }

    private fun toInst(pointer: Long): GoInst = GoInst(pointer, "")

    private fun toMethod(pointer: Long): GoMethod = GoMethod(pointer, "")

    private fun toType(pointer: Long): GoType = GoType(pointer, "")

    // ------------ region: utils

    // ------------ region: test

    fun getNumber() = NalimBridge.getNumber()

    // ------------ region: test
}