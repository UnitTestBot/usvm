package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx

fun <Sort : USort> UWritableMemory<*>.write(ref: ULValue<*, Sort>, value: UExpr<Sort>) {
    write(ref, value, value.uctx.trueExpr)
}

// Built-in KContext.bvToBool has identical implementation.
fun UContext<*>.boolToFpSort(expr: UExpr<UBoolSort>) =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

/**
 * It is not a cast. It's a function that builds logical condition, e.g., for if statement.
 *
 * Note that 0.1 != false and 0.1 != true, so this operation must not be used for comparison of fp and bool values.
 */
fun UContext<*>.fpToBoolForConditions(expr: UExpr<KFp64Sort>) =
    mkIte(mkFpEqualExpr(expr, mkFp64(0.0)), mkFalse(), mkTrue())

// fun UExpr<out USort>.unwrapIfRequired(): UExpr<out USort> = if (this is TSWrappedValue) value else this

fun <K, V> MutableMap<K, MutableSet<V>>.copy(): MutableMap<K, MutableSet<V>> = this.entries.associate { (k, v) ->
    k to v.toMutableSet()
}.toMutableMap()
