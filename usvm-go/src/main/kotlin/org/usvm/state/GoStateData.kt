package org.usvm.state

import org.jacodb.go.api.GoType
import org.usvm.GoCall
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
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

    internal val pointerConversions: MutableMap<UHeapRef, GoPointerConversion> = hashMapOf()

    internal var pendingArrayCopy: GoArrayCopyOperation? = null
    internal var builtinResult: UExpr<out USort>? = null

    private val deferredCalls: ArrayDeque<ArrayDeque<GoCall>> = ArrayDeque()

    val flowStatus: GoFlowStatus
        get() = flowStack.last()

    fun pushDeferredFrame() {
        deferredCalls.addLast(ArrayDeque())
    }

    fun popDeferredFrame() {
        deferredCalls.removeLast()
    }

    fun getDeferredCalls(): ArrayDeque<GoCall> = deferredCalls.last()

    fun addDeferredCall(call: GoCall) {
        getDeferredCalls().addLast(call)
    }

    fun clone(): GoStateData = GoStateData(clonedFlowStack()).also {
        it.pointerTargets.putAll(pointerTargets)
        it.pointerConversions.putAll(pointerConversions)
        it.arrayViews.putAll(arrayViews)
        it.pendingArrayCopy = pendingArrayCopy
        it.builtinResult = builtinResult
        deferredCalls.forEach { calls -> it.deferredCalls.addLast(ArrayDeque(calls)) }
    }

    fun mergeWith(other: GoStateData): GoStateData? {
        val thisCalls = deferredCalls.map { it.toList() }
        val otherCalls = other.deferredCalls.map { it.toList() }
        val sameCalls = thisCalls == otherCalls
        val sameOperation = pendingArrayCopy == other.pendingArrayCopy && builtinResult == other.builtinResult
        if (!sameOperation) return null

        if (flowStack.toList() != other.flowStack.toList() || !sameCalls ||
            arrayViews != other.arrayViews || pointerTargets != other.pointerTargets ||
            pointerConversions != other.pointerConversions
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

internal data class GoPointerConversion(
    val source: UHeapRef,
    val sourceType: GoType,
    val targetType: GoType,
)
