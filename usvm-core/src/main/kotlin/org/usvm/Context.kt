package org.usvm

import org.ksmt.KAst
import org.ksmt.KContext
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.utils.asExpr

open class UContext: KContext() {
    // TODO: Caches
    // TODO: mk functions for new expressions

    val addressSort: UAddressSort = UAddressSort(this)
    val sizeSort: USizeSort = mkBv32Sort()

    val zeroSize: USizeExpr = sizeSort.sampleValue()

    val nullRef = UConcreteHeapRef(nullAddress, this)

    fun <Sort: USort> mkDefault(sort: Sort): UExpr<Sort> =
        when(sort) {
            is UAddressSort -> nullRef.asExpr(sort)
            else -> sort.sampleValue()
        }

    // TODO: delegate it to KSMT
    fun mkNotSimplified(expr: UBoolExpr) =
        when(expr) {
            is UNotExpr -> expr.arg
            else -> expr.ctx.mkNot(expr)
        }
}

fun USort.defaultValue() =
    when(ctx) {
        is UContext -> (ctx as UContext).mkDefault(this)
        else -> sampleValue()
    }

val KAst.uctx
    get() = ctx as UContext
