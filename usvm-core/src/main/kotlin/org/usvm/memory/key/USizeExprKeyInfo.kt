package org.usvm.memory.key

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KInt32NumExpr
import io.ksmt.sort.KIntSort
import io.ksmt.utils.cast
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UConcreteSize
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.apply
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.regions.IntIntervalsRegion

typealias USizeRegion = IntIntervalsRegion

/**
 * Provides information about numeric values used as symbolic collection keys.
 */
abstract class USizeExprKeyInfo<USizeSort : USort> : USymbolicCollectionKeyInfo<UExpr<USizeSort>, USizeRegion> {
    override fun mapKey(key: UExpr<USizeSort>, transformer: UTransformer<*, *>?): UExpr<USizeSort> = transformer.apply(key)

    override fun eqSymbolic(ctx: UContext<*>, key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): UBoolExpr =
        ctx.mkEq(key1, key2)

    override fun eqConcrete(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): Boolean =
        key1 === key2

    override fun topRegion() =
        IntIntervalsRegion.universe()

    override fun bottomRegion() =
        IntIntervalsRegion.empty()

    companion object {
        inline fun <reified USizeSort : USort> create(): USizeExprKeyInfo<USizeSort> = when (USizeSort::class) {
            UBv32Sort::class -> USizeExprBv32KeyInfo.cast()
            KIntSort::class -> USizeExprInt32KeyInfo.cast()
            else -> error("Cannot instantiate USizeExprKeyInfo with sort ${USizeSort::class}")
        }
    }
}

object USizeExprBv32KeyInfo : USizeExprKeyInfo<UBv32Sort>() {
    override fun cmpSymbolicLe(ctx: UContext<*>, key1: UExpr<UBv32Sort>, key2: UExpr<UBv32Sort>): UBoolExpr =
        ctx.mkBvSignedLessOrEqualExpr(key1, key2)

    override fun cmpConcreteLe(key1: UExpr<UBv32Sort>, key2: UExpr<UBv32Sort>): Boolean =
        key1 == key2 || (key1 is KBitVec32Value && key2 is KBitVec32Value && key1.numberValue <= key2.numberValue)

    override fun keyToRegion(key: UExpr<UBv32Sort>) =
        when (key) {
            is KBitVec32Value -> IntIntervalsRegion.point(key.numberValue)
            else -> topRegion()
        }

    override fun keyRangeRegion(from: UExpr<UBv32Sort>, to: UExpr<UBv32Sort>) =
        when (from) {
            is KBitVec32Value ->
                when (to) {
                    is UConcreteSize -> IntIntervalsRegion.ofClosed(from.numberValue, to.numberValue)
                    else -> topRegion()
                }

            else -> topRegion()
        }
}

object USizeExprInt32KeyInfo : USizeExprKeyInfo<KIntSort>() {
    override fun cmpSymbolicLe(ctx: UContext<*>, key1: UExpr<KIntSort>, key2: UExpr<KIntSort>): UBoolExpr =
        ctx.mkArithLe(key1, key2)

    override fun cmpConcreteLe(key1: UExpr<KIntSort>, key2: UExpr<KIntSort>): Boolean =
        key1 == key2 || (key1 is KInt32NumExpr && key2 is KInt32NumExpr && key1.value <= key2.value)

    override fun keyToRegion(key: UExpr<KIntSort>) =
        when (key) {
            is KInt32NumExpr -> IntIntervalsRegion.point(key.value)
            else -> topRegion()
        }

    override fun keyRangeRegion(from: UExpr<KIntSort>, to: UExpr<KIntSort>) =
        when (from) {
            is KInt32NumExpr ->
                when (to) {
                    is KInt32NumExpr -> IntIntervalsRegion.ofClosed(from.value, to.value)
                    else -> topRegion()
                }

            else -> topRegion()
        }
}

