package org.usvm.collection.string

import io.ksmt.cache.hash
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx

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

    override fun emptyCollection(): USymbolicCollection<UInputStringId, UHeapRef, UStringSort> {
        val updates = UFlatUpdates<UHeapRef, UStringSort>(keyInfo())
        return USymbolicCollection(this, updates)
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun toString(): String = "inputString"

    override fun equals(other: Any?): Boolean =
        this === other

    override fun hashCode(): Int = hash(javaClass, sort)
}
