package org.usvm.state

import org.jacodb.go.api.GoMethod
import org.usvm.GoCall

enum class GoFlowStatus {
    NORMAL,
    PANIC,
    DEFER,
}

class GoStateData(
    val flowStack: ArrayDeque<GoFlowStatus> = ArrayDeque(),
) {
    private val deferredCalls: MutableMap<GoMethod, ArrayDeque<GoCall>> = hashMapOf()

    val flowStatus: GoFlowStatus
        get() = flowStack.last()

    fun getDeferredCalls(method: GoMethod): ArrayDeque<GoCall> = deferredCalls[method] ?: ArrayDeque()

    fun addDeferredCall(method: GoMethod, call: GoCall) {
        if (method !in deferredCalls) {
            deferredCalls[method] = ArrayDeque()
        }
        deferredCalls[method]!!.addLast(call)
    }

    fun clone(): GoStateData = GoStateData(clonedFlowStack()).mergeWith(this)

    fun mergeWith(other: GoStateData) = GoStateData(clonedFlowStack()).also {
        for (entry in other.deferredCalls) {
            entry.value.forEach { c -> it.addDeferredCall(entry.key, c) }
        }
    }

    private fun clonedFlowStack(): ArrayDeque<GoFlowStatus> {
        val newStack = ArrayDeque<GoFlowStatus>()
        newStack.addAll(flowStack)
        return newStack
    }
}