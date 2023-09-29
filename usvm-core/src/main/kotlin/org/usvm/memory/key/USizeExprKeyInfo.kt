package org.usvm.memory.key

import io.ksmt.utils.cast
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.apply
import org.usvm.getIntValue
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.mkSizeLeExpr
import org.usvm.regions.IntIntervalsRegion
import org.usvm.uctx
import org.usvm.withSizeSort

typealias USizeRegion = IntIntervalsRegion

/**
 * Provides information about numeric values used as symbolic collection keys.
 */
class USizeExprKeyInfo<USizeSort : USort> private constructor() :
    USymbolicCollectionKeyInfo<UExpr<USizeSort>, USizeRegion> {
    override fun mapKey(
        key: UExpr<USizeSort>,
        transformer: UTransformer<*, *>?,
    ): UExpr<USizeSort> = transformer.apply(key)

    override fun eqSymbolic(ctx: UContext<*>, key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): UBoolExpr =
        ctx.mkEq(key1, key2)

    override fun cmpSymbolicLe(ctx: UContext<*>, key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): UBoolExpr =
        ctx.withSizeSort<USizeSort>().mkSizeLeExpr(key1, key2)

    override fun cmpConcreteLe(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): Boolean {
        if (key1 == key2) {
            return true
        }

        val ctx = key1.uctx.withSizeSort<USizeSort>()
        val firstIntValue = ctx.getIntValue(key1) ?: return false
        val secondIntValue = ctx.getIntValue(key2) ?: return false

        return firstIntValue <= secondIntValue
    }

    override fun keyToRegion(key: UExpr<USizeSort>): IntIntervalsRegion {
        val ctx = key.uctx.withSizeSort<USizeSort>()

        return ctx.getIntValue(key)?.let {
            IntIntervalsRegion.point(it)
        } ?: topRegion()
    }

    override fun keyRangeRegion(from: UExpr<USizeSort>, to: UExpr<USizeSort>): IntIntervalsRegion {
        val ctx = from.uctx.withSizeSort<USizeSort>()
        val fromIntValue = ctx.getIntValue(from) ?: return topRegion()
        val todIntValue = ctx.getIntValue(to) ?: return topRegion()

        return IntIntervalsRegion.ofClosed(fromIntValue, todIntValue)
    }

    override fun eqConcrete(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): Boolean =
        key1 === key2

    override fun topRegion() =
        IntIntervalsRegion.universe()

    override fun bottomRegion() =
        IntIntervalsRegion.empty()

    companion object {
        private val sizeExprKeyInfo: USizeExprKeyInfo<Nothing> by lazy { USizeExprKeyInfo() }

        // Use this class as a parametrized singleton
        operator fun <USizeSort : USort> invoke(): USizeExprKeyInfo<USizeSort> = sizeExprKeyInfo.cast()
    }
}
