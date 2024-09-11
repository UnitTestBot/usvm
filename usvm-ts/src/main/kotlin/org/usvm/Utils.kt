package org.usvm

import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsMethod
import org.usvm.constraints.UTypeConstraints
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.UWritableMemory

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

fun UContext<*>.boolToFpSort(expr: UExpr<UBoolSort>) = mkIte(expr, mkFp64(1.0), mkFp64(0.0))

class TSMemory(
    internal val ctx: TSContext,
    override val types: UTypeConstraints<EtsType>,
) : UMemory<EtsType, EtsMethod>(ctx, types) {

    override fun <Key, Sort : USort> read(lvalue: ULValue<Key, Sort>): UExpr<Sort> {
        val expr = readUnsafe(lvalue)
        val sort = lvalue.sort
        if (expr.sort == sort) return expr.cast()

        assert(expr is TSWrappedValue)
        return (expr as TSWrappedValue).asSort(sort).cast()
            ?: error("Unsupported behaviour: lvalue = $lvalue, desired sort = $sort")
    }
}

