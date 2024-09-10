package org.usvm.collection.string

import io.ksmt.cache.hash
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.regions.emptyRegionTree
import org.usvm.uctx
import org.usvm.withSizeSort

/**
 * A collection id for a collection storing non-deterministic strings.
 */
class UInputStringId internal constructor(ctx: UContext<*>)
    : USymbolicCollectionId<UHeapRef, UStringSort, UInputStringId>
{
    override val sort: UStringSort = ctx.stringSort

    override fun instantiate(
        collection: USymbolicCollection<UInputStringId, UHeapRef, UStringSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UStringExpr {
        if (composer == null) {
            return sort.uctx.mkStringFromLanguage(key)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UHeapRef,
        value: UStringExpr,
        guard: UBoolExpr
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) =
        UStringLValue(key)

    override fun emptyRegion(): USymbolicCollection<UInputStringId, UHeapRef, UStringSort> {
        val updates = UFlatUpdates<UHeapRef, UStringSort>(keyInfo())
        return USymbolicCollection(this, updates)
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun toString(): String = "inputString"

    override fun equals(other: Any?): Boolean =
        this === other

    override fun hashCode(): Int = hash(javaClass, sort)
}
