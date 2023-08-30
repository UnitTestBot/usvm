package org.usvm.memory.key

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.util.TrivialRegion

object USingleKeyInfo : USymbolicCollectionKeyInfo<Unit, TrivialRegion> {
    override fun mapKey(key: Unit, composer: UComposer<*>?) = key
    override fun eqSymbolic(ctx: UContext, key1: Unit, key2: Unit): UBoolExpr = ctx.trueExpr
    override fun eqConcrete(key1: Unit, key2: Unit): Boolean = true
    override fun cmpSymbolicLe(ctx: UContext, key1: Unit, key2: Unit): UBoolExpr = singleKeyError()
    override fun cmpConcreteLe(key1: Unit, key2: Unit): Boolean = singleKeyError()
    override fun keyToRegion(key: Unit): TrivialRegion = singleKeyError()
    override fun keyRangeRegion(from: Unit, to: Unit): TrivialRegion = singleKeyError()
    override fun topRegion(): TrivialRegion = singleKeyError()
    override fun bottomRegion(): TrivialRegion = singleKeyError()

    private fun singleKeyError(): Nothing =
        error("Unexpected operation on single key")
}