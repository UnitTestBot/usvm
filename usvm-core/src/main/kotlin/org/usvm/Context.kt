package org.usvm

import org.ksmt.KAst
import org.ksmt.KContext
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue

open class UContext: KContext() {
    // TODO: Caches
    // TODO: mk functions for new expressions

    val addressSort: UAddressSort = UAddressSort(this)
    val sizeSort: USizeSort = mkBv32Sort()

    val zeroSize: USizeExpr = sizeSort.sampleValue()

    val nullRef = UConcreteHeapRef(nullAddress, this)

    fun mkDefault(sort: USort): UExpr =
        when(sort) {
            is UAddressSort -> nullRef
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