package org.usvm.state

import io.ksmt.utils.asExpr
import org.jacodb.go.api.GoType
import org.usvm.UExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.interpreter.GoStepScope
import org.usvm.memory.GoArrayView
import org.usvm.memory.readGoArrayIndex
import org.usvm.memory.writeGoArrayIndex
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeLtExpr

internal data class GoArrayValueCopy(
    val source: GoArrayView,
    val destination: GoArrayView,
    val length: UExpr<USizeSort>,
    val elementType: GoType,
)

internal data class GoArrayCopyOperation(
    val sourceState: GoState,
    val copies: List<GoArrayValueCopy>,
    val result: UExpr<out USort>,
    val copyIndex: Int = 0,
    val elementIndex: Int = 0,
)

// Perform one element per machine step so symbolic lengths remain subject to normal analysis budgets.
internal fun advanceArrayValueCopy(scope: GoStepScope) {
    val operation = scope.calcOnState { checkNotNull(data.pendingArrayCopy) }
    val copy = operation.copies.getOrNull(operation.copyIndex)
    if (copy == null) {
        scope.doWithState {
            data.pendingArrayCopy = null
            data.builtinResult = operation.result
        }
        return
    }

    val ctx = operation.sourceState.ctx
    val index = ctx.mkSizeExpr(operation.elementIndex)
    val hasElement = ctx.mkSizeLtExpr(index, copy.length)
    scope.fork(hasElement, blockOnFalseState = {
        data.pendingArrayCopy = operation.copy(copyIndex = operation.copyIndex + 1, elementIndex = 0)
    }) ?: return

    scope.doWithState {
        val sourceIndex = ctx.mkSizeAddExpr(copy.source.offset, index)
        val sourceValue = operation.sourceState.memory.readGoArrayIndex(
            copy.source.backing,
            sourceIndex,
            copy.source.storageType,
            ctx.addressSort,
        )
        val copied = if (sourceValue == ctx.nullRef) {
            sampleValue(copy.elementType)
        } else {
            copyValue(sourceValue, copy.elementType, source = operation.sourceState)
        }
        val destinationIndex = ctx.mkSizeAddExpr(copy.destination.offset, index)

        memory.writeGoArrayIndex(
            copy.destination.backing,
            destinationIndex,
            copy.destination.storageType,
            ctx.addressSort,
            copied.asExpr(ctx.addressSort),
            guard = ctx.trueExpr,
        )
        data.pendingArrayCopy = operation.copy(elementIndex = operation.elementIndex + 1)
    }
}
