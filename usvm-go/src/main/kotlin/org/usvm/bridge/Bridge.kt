package org.usvm.bridge

import org.usvm.api.GoApi
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoMethodInfo
import org.usvm.domain.GoType
import org.usvm.util.GoResult

interface Bridge {
    // ------------ region: initialize
    fun initialize(file: String, entrypoint: String, debug: Boolean): GoResult
    // ------------ region: initialize

    // ------------ region: machine
    fun getMain(): GoMethod
    fun getMethod(name: String): GoMethod
    // ------------ region: machine

    // ------------ region: application graph
    fun predecessors(inst: GoInst): Array<GoInst>
    fun successors(inst: GoInst): Array<GoInst>
    fun callees(inst: GoInst): Array<GoMethod>
    fun callers(method: GoMethod): Array<GoInst>
    fun entryPoints(method: GoMethod): Array<GoInst>
    fun exitPoints(method: GoMethod): Array<GoInst>
    fun methodOf(inst: GoInst): GoMethod
    fun statementsOf(method: GoMethod): Array<GoInst>
    // ------------ region: application graph

    // ------------ region: type system
    fun getAnyType(): GoType
    fun findSubTypes(type: GoType): Array<GoType>
    fun isInstantiable(type: GoType): Boolean
    fun isFinal(type: GoType): Boolean
    fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean
    fun isSupertype(supertype: GoType, type: GoType): Boolean
    // ------------ region: type system

    // ------------ region: interpreter
    fun methodInfo(method: GoMethod): GoMethodInfo
    // ------------ region: interpreter

    // ------------ region: api
    fun start(api: GoApi): Int
    fun step(api: GoApi, inst: GoInst): GoInst
    // ------------ region: api
}

typealias AnyPointer = Long
typealias InstPointer = Long
typealias MethodPointer = Long
typealias TypePointer = Long

enum class BridgeType {
    JNA, JNI
}

fun mkBridge(type: BridgeType): Bridge = when(type) {
    BridgeType.JNA -> GoJnaBridge()
    BridgeType.JNI -> GoJniBridge()
}