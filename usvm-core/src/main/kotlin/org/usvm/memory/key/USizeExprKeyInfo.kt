package org.usvm.memory.key

import org.usvm.UBoolExpr
import org.usvm.UConcreteSize
import org.usvm.UContext
import org.usvm.USizeExpr
import org.usvm.UTransformer
import org.usvm.apply
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.regions.IntIntervalsRegion

typealias USizeRegion = IntIntervalsRegion

/**
 * Provides information about numeric values used as symbolic collection keys.
 */
object USizeExprKeyInfo : USymbolicCollectionKeyInfo<USizeExpr, USizeRegion> {
    override fun mapKey(key: USizeExpr, transformer: UTransformer<*>?): USizeExpr = transformer.apply(key)

    override fun eqSymbolic(ctx: UContext, key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        ctx.mkEq(key1, key2)

    override fun eqConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 === key2

    override fun cmpSymbolicLe(ctx: UContext, key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        ctx.mkBvSignedLessOrEqualExpr(key1, key2)

    override fun cmpConcreteLe(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 == key2 || (key1 is UConcreteSize && key2 is UConcreteSize && key1.numberValue <= key2.numberValue)

    override fun keyToRegion(key: USizeExpr) =
        when (key) {
            is UConcreteSize -> IntIntervalsRegion.point(key.numberValue)
            else -> topRegion()
        }

    override fun keyRangeRegion(from: USizeExpr, to: USizeExpr) =
        when (from) {
            is UConcreteSize ->
                when (to) {
                    is UConcreteSize -> IntIntervalsRegion.ofClosed(from.numberValue, to.numberValue)
                    else -> topRegion()
                }

            else -> topRegion()
        }

    override fun topRegion() =
        IntIntervalsRegion.universe()

    override fun bottomRegion() =
        IntIntervalsRegion.empty()
}
