package org.usvm.memory.collection.key

import org.usvm.UBoolExpr
import org.usvm.UConcreteSize
import org.usvm.USizeExpr
import org.usvm.USizeType
import org.usvm.uctx
import org.usvm.util.SetRegion

// TODO: change it to intervals region
typealias USizeRegion = SetRegion<USizeType>

/**
 * Provides information about numeric values used as symbolic collection keys.
 */
object USizeExprKeyInfo : USymbolicCollectionKeyInfo<USizeExpr, USizeRegion> {
    override fun eqSymbolic(key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        key1.uctx.mkEq(key1, key2)

    override fun eqConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 === key2

    override fun cmpSymbolic(key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        key1.ctx.mkBvSignedLessOrEqualExpr(key1, key2)

    override fun cmpConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 == key2 || (key1 is UConcreteSize && key2 is UConcreteSize && key1.numberValue <= key2.numberValue)

    override fun keyToRegion(key: USizeExpr) =
        when (key) {
            is UConcreteSize -> SetRegion.singleton(key.numberValue)
            else -> SetRegion.universe()
        }

    override fun keyRangeRegion(from: USizeExpr, to: USizeExpr) =
        when (from) {
            is UConcreteSize ->
                when (to) {
                    is UConcreteSize -> SetRegion.ofSequence((from.numberValue..to.numberValue).asSequence())
                    else -> SetRegion.universe()
                }

            else -> SetRegion.universe()
        }

    override fun topRegion() =
        SetRegion.universe<USizeType>()

    override fun bottomRegion() =
        SetRegion.empty<USizeType>()
}
