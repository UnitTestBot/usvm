package org.usvm.memory.collection.key

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.util.SetRegion

typealias UHeapRefRegion = SetRegion<UConcreteHeapAddress>

/**
 * Provides information about heap references used as symbolic collection keys.
 */
object UHeapRefKeyInfo: USymbolicCollectionKeyInfo<UHeapRef, UHeapRefRegion> {
    override fun eqSymbolic(ctx: UContext, key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        ctx.mkHeapRefEq(key1, key2)

    override fun eqConcrete(key1: UHeapRef, key2: UHeapRef): Boolean =
        key1 == key2

    override fun cmpSymbolic(ctx: UContext, key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        error("Heap references should not be compared!")

    override fun cmpConcrete(key1: UHeapRef, key2: UHeapRef): Boolean =
        error("Heap references should not be compared!")

    override fun keyToRegion(key: UHeapRef) =
        if (key is UConcreteHeapRef){
            SetRegion.singleton(key.address)
        } else {
            SetRegion.universe()
        }

    override fun keyRangeRegion(from: UHeapRef, to: UHeapRef) =
        error("This should not be called!")

    override fun topRegion() =
        SetRegion.universe<UConcreteHeapAddress>()

    override fun bottomRegion() =
        SetRegion.empty<UConcreteHeapAddress>()
}
