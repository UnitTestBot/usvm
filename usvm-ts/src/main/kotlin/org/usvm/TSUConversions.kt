package org.usvm

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsType

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>
) {

    private val ctx = baseExpr.tctx

    @Suppress("UNCHECKED_CAST")
    fun transform(expr: UExpr<out USort>): Pair<UExpr<out USort>, EtsType> = with(ctx) {
        when {
            expr is TSWrappedValue -> transform(expr.value.sort) to expr.type
            expr is UIntepretedValue -> transform(expr.sort) to EtsAnyType
            expr.sort == addressSort -> transformRef(expr as UExpr<UAddressSort>)
            else -> error("Should not be called")
        }
    }

    private fun transform(sort: USort): UExpr<out USort> = with(ctx) {
        when (sort) {
            fp64Sort -> asFp64()
            boolSort -> asBool()
            else -> error("")
        }
    }

    private fun transformRef(expr: UExpr<UAddressSort>): Pair<UExpr<out USort>, EtsType> = TODO()

    @Suppress("UNCHECKED_CAST")
    fun asFp64(): UExpr<KFp64Sort> = when (baseExpr.sort) {
        ctx.fp64Sort -> baseExpr as UExpr<KFp64Sort>
        ctx.boolSort -> if (extractBool(baseExpr)) ctx.mkFp64(1.0) else ctx.mkFp64(0.0)
        else -> ctx.mkFp64(0.0)
    }

    @Suppress("UNCHECKED_CAST")
    fun asBool(): UExpr<KBoolSort> = when (baseExpr.sort) {
        ctx.boolSort -> baseExpr as UExpr<KBoolSort>
        ctx.fp64Sort -> if (extractDouble(baseExpr) == 1.0) ctx.mkTrue() else ctx.mkFalse()
        else -> ctx.mkFalse()
    }
}
