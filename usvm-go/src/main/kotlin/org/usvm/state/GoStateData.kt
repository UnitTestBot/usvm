package org.usvm.state

import org.jacodb.go.api.GoMethod
import org.usvm.GoCall
import org.usvm.UHeapRef
import org.usvm.memory.GoArrayView
import org.usvm.memory.ULValue

enum class GoFlowStatus {
    NORMAL,
    PANIC,
    DEFER,
}

class GoStateData(
    val flowStack: ArrayDeque<GoFlowStatus> = ArrayDeque(),
) {
    internal val arrayViews: MutableMap<UHeapRef, GoArrayView> = hashMapOf()

    internal val pointerTargets: MutableMap<UHeapRef, ULValue<*, *>> = hashMapOf()

    private val deferredCalls: MutableMap<GoMethod, ArrayDeque<GoCall>> = hashMapOf()

    val flowStatus: GoFlowStatus
        get() = flowStack.last()

    fun getDeferredCalls(method: GoMethod): ArrayDeque<GoCall> = deferredCalls[method] ?: ArrayDeque()

    fun addDeferredCall(method: GoMethod, call: GoCall) {
        deferredCalls.getOrPut(method) { ArrayDeque() }.addLast(call)
    }

    fun clone(): GoStateData = GoStateData(clonedFlowStack()).also {
        it.pointerTargets.putAll(pointerTargets)
        it.arrayViews.putAll(arrayViews)
        for ((method, calls) in deferredCalls) {
            calls.forEach { call -> it.addDeferredCall(method, call) }
        }
    }

    fun mergeWith(other: GoStateData): GoStateData? {
        val thisCalls = deferredCalls.mapValues { it.value.toList() }
        val otherCalls = other.deferredCalls.mapValues { it.value.toList() }
        val sameCalls = thisCalls == otherCalls
        if (flowStack.toList() != other.flowStack.toList() || !sameCalls ||
            arrayViews != other.arrayViews || pointerTargets != other.pointerTargets
        ) {
            return null
        }
        return clone()
    }

    private fun clonedFlowStack(): ArrayDeque<GoFlowStatus> {
        val newStack = ArrayDeque<GoFlowStatus>()
        newStack.addAll(flowStack)
        return newStack
    }
}
