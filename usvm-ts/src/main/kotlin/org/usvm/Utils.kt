package org.usvm

import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

fun UContext<*>.boolToFpSort(expr: UExpr<UBoolSort>) = mkIte(expr, mkFp64(1.0), mkFp64(0.0))

