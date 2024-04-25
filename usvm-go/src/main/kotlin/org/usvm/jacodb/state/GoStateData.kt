package org.usvm.jacodb.state

import org.usvm.jacodb.GoCall
import org.usvm.jacodb.GoInst
import org.usvm.jacodb.GoMethod

enum class GoFlowStatus {
    NORMAL,
    DEFER,
}

class GoStateData(
    var flowStatus: GoFlowStatus = GoFlowStatus.NORMAL,
    var recoverInst: GoInst? = null,
) {
    private val deferredCalls: MutableMap<GoMethod, ArrayDeque<GoCall>> = hashMapOf()

    private val deferInst: MutableMap<GoMethod, GoInst> = hashMapOf()
    private val deferNextInst: MutableMap<GoMethod, GoInst> = hashMapOf()

    private val recover: MutableMap<GoMethod, GoInst> = hashMapOf()

    fun getDeferredCalls(method: GoMethod): ArrayDeque<GoCall> = deferredCalls[method] ?: ArrayDeque()

    fun addDeferredCall(method: GoMethod, call: GoCall) {
        if (method !in deferredCalls) {
            deferredCalls[method] = ArrayDeque()
        }
        deferredCalls[method]!!.addLast(call)
    }

    fun getDeferInst(method: GoMethod): GoInst? = deferInst[method]

    fun setDeferInst(method: GoMethod, inst: GoInst) {
        deferInst[method] = inst
    }

    fun getDeferNextInst(method: GoMethod): GoInst? = deferNextInst[method]

    fun setDeferNextInst(method: GoMethod, inst: GoInst?) {
        if (inst == null) return
        deferNextInst[method] = inst
    }

    fun getRecover(method: GoMethod): GoInst? = recover[method]

    fun setRecover(method: GoMethod, inst: GoInst) {
        recover[method] = inst
    }

    fun clone(): GoStateData = GoStateData(flowStatus, recoverInst).mergeWith(this)

    fun mergeWith(other: GoStateData) = GoStateData(flowStatus, recoverInst).also {
        for (entry in other.deferredCalls) {
            entry.value.forEach { c -> it.addDeferredCall(entry.key, c) }
        }
        for (entry in other.deferInst) {
            it.setDeferInst(entry.key, entry.value)
        }
        for (entry in other.deferNextInst) {
            it.setDeferNextInst(entry.key, entry.value)
        }
        for (entry in other.recover) {
            it.setRecover(entry.key, entry.value)
        }
    }
}