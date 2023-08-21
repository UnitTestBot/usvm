package org.usvm.memory.collection.key

import org.usvm.UBoolExpr
import org.usvm.util.TrivialRegion

object UNoKeyInfo : USymbolicCollectionKeyInfo<Unit, TrivialRegion> {
    override fun eqSymbolic(key1: Unit, key2: Unit): UBoolExpr = noKeyError()
    override fun eqConcrete(key1: Unit, key2: Unit): Boolean = noKeyError()
    override fun cmpSymbolic(key1: Unit, key2: Unit): UBoolExpr = noKeyError()
    override fun cmpConcrete(key1: Unit, key2: Unit): Boolean = noKeyError()
    override fun keyToRegion(key: Unit): TrivialRegion = noKeyError()
    override fun keyRangeRegion(from: Unit, to: Unit): TrivialRegion = noKeyError()
    override fun topRegion(): TrivialRegion = noKeyError()
    override fun bottomRegion(): TrivialRegion = noKeyError()

    private fun noKeyError(): Nothing =
        error("No key")
}
