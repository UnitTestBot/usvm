package org.usvm

import io.ksmt.sort.KFp64Sort
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

// Built-in KContext.bvToBool has identical implementation.
fun UContext<*>.boolToFpSort(expr: UExpr<UBoolSort>) =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

fun UContext<*>.fpToBoolSort(expr: UExpr<KFp64Sort>) =
    mkIte(mkFpEqualExpr(expr, mkFp64(0.0)), mkFalse(), mkTrue())

fun UExpr<*>.unwrapJoinedExpr(ctx: UContext<*>): UExpr<out USort> =
    if (this is UJoinedBoolExpr) ctx.mkAnd(exprs) else this

fun <K, V> MutableMap<K, MutableSet<V>>.copy(): MutableMap<K, MutableSet<V>> = this.entries.associate { (k, v) ->
    k to v.toMutableSet()
}.toMutableMap()

/**
 * Puts an element in a [MutableSet].
 *
 * @return [element] if collection was modified, null otherwise.
 */
fun <T> MutableSet<T>.putOrNull(element: T): T? = if (add(element)) element else null
