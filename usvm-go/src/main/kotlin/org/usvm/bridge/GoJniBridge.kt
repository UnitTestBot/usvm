package org.usvm.bridge

import org.usvm.api.GoApi
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoMethodInfo
import org.usvm.domain.GoType
import org.usvm.util.GoResult

class GoJniBridge : Bridge {
    private val jniBridge = JniBridge()

    // ------------ region: initialize

    override fun initialize(file: String, entrypoint: String, debug: Boolean): GoResult {
        return GoResult("initialize", jniBridge.initialize(file.toByteArray(), entrypoint.toByteArray(), debug))
    }

    // ------------ region: initialize

    // ------------ region: shutdown

    override fun shutdown(): GoResult {
        return GoResult("shutdown", jniBridge.shutdown())
    }

    // ------------ region: shutdown

    // ------------ region: machine

    override fun getMain(): GoMethod = toMethod(jniBridge.getMain())

    override fun getMethod(name: String) = toMethod(jniBridge.getMethod(name.toByteArray()))

    // ------------ region: machine

    // ------------ region: application graph

    override fun predecessors(inst: GoInst): Array<GoInst> = toInstArray(jniBridge.predecessors(inst.pointer))

    override fun successors(inst: GoInst): Array<GoInst> = toInstArray(jniBridge.successors(inst.pointer))

    override fun callees(inst: GoInst): Array<GoMethod> = toMethodArray(jniBridge.callees(inst.pointer))

    override fun callers(method: GoMethod): Array<GoInst> = toInstArray(jniBridge.callers(method.pointer))

    override fun entryPoints(method: GoMethod): Array<GoInst> = toInstArray(jniBridge.entryPoints(method.pointer))

    override fun exitPoints(method: GoMethod): Array<GoInst> = toInstArray(jniBridge.exitPoints(method.pointer))

    override fun methodOf(inst: GoInst): GoMethod = toMethod(jniBridge.methodOf(inst.pointer))

    override fun statementsOf(method: GoMethod): Array<GoInst> = toInstArray(jniBridge.statementsOf(method.pointer))

    // ------------ region: application graph

    // ------------ region: type system

    override fun getAnyType(): GoType = toType(jniBridge.getAnyType())

    override fun findSubTypes(type: GoType): Array<GoType> = toTypeArray(jniBridge.findSubTypes(type.pointer))

    override fun isInstantiable(type: GoType): Boolean = jniBridge.isInstantiable(type.pointer)

    override fun isFinal(type: GoType): Boolean = jniBridge.isFinal(type.pointer)

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        return jniBridge.hasCommonSubtype(type.pointer, types.map { it.pointer }.toLongArray(), types.size)
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean {
        return jniBridge.isSupertype(supertype.pointer, type.pointer)
    }

    // ------------ region: type system

    // ------------ region: interpreter

    override fun methodInfo(method: GoMethod): GoMethodInfo = toMethodInfo(jniBridge.methodInfo(method.pointer))

    // ------------ region: interpreter

    // ------------ region: api

    fun withApi(api: GoApi): GoJniBridge = this.also { jniBridge.withApi(api) }

    override fun start(): Int = jniBridge.start()

    override fun step(inst: GoInst): GoInst = toInst(jniBridge.step(inst.pointer))

    // ------------ region: api

    // ------------ region: utils

    private inline fun <reified R> toArray(
        array: LongArray,
        mapper: (Long) -> (R)
    ): Array<R> {
        return array.map(mapper).toTypedArray()
    }

    private fun toInstArray(array: LongArray): Array<GoInst> {
        return toArray(array) { toInst(it) }
    }

    private fun toMethodArray(array: LongArray): Array<GoMethod> {
        return toArray(array) { toMethod(it) }
    }

    private fun toTypeArray(array: LongArray): Array<GoType> {
        return toArray(array) { toType(it) }
    }

    private fun toInst(pointer: Long): GoInst = GoInst(pointer, "")

    private fun toMethod(pointer: Long): GoMethod = GoMethod(pointer, "")

    private fun toMethodInfo(methodInfo: IntArray): GoMethodInfo = GoMethodInfo(methodInfo[0], methodInfo[1])

    private fun toType(pointer: Long): GoType = GoType(pointer, "")

    // ------------ region: utils

    // ------------ region: test

    fun getNumber() = jniBridge.getNumber()

    fun initialize2(file: String, entrypoint: String, debug: Boolean): Long {
        return jniBridge.initialize2(file, entrypoint, debug)
    }

    // ------------ region: test
}