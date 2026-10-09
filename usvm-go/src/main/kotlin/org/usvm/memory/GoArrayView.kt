package org.usvm.memory

import io.ksmt.expr.KIteExpr
import org.jacodb.go.api.GoType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.state.GoState

internal data class GoArrayView(
    val backing: UHeapRef,
    val storageType: GoType,
    val offset: UExpr<USizeSort>,
    val length: UExpr<USizeSort>,
    val capacity: UExpr<USizeSort>,
)

internal fun GoState.arrayView(
    reference: UHeapRef,
    type: GoType,
    sourceMemory: UReadOnlyMemory<GoType> = memory,
): GoArrayView {
    data.arrayViews[reference]?.let { return it }

    if (reference is KIteExpr) {
        val positive = arrayView(reference.trueBranch, type, sourceMemory)
        val negative = arrayView(reference.falseBranch, type, sourceMemory)
        check(positive.storageType == negative.storageType) { "Array view storage types differ" }
        return GoArrayView(
            ctx.mkIte(reference.condition, positive.backing, negative.backing),
            positive.storageType,
            offset = ctx.mkIte(reference.condition, positive.offset, negative.offset),
            length = ctx.mkIte(reference.condition, positive.length, negative.length),
            capacity = ctx.mkIte(reference.condition, positive.capacity, negative.capacity),
        )
    }

    val zero = ctx.mkSizeExpr(0)
    val length = ctx.mkIte(
        ctx.mkHeapRefEq(reference, ctx.nullRef),
        trueBranch = { zero },
        falseBranch = { sourceMemory.readGoArrayLength(reference, type, ctx.sizeSort) },
    )
    return GoArrayView(reference, type.arrayStorageType(), offset = zero, length = length, capacity = length)
}
