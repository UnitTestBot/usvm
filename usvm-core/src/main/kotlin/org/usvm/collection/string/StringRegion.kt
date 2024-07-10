package org.usvm.collection.string

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef
import org.usvm.memory.guardedWrite
import org.usvm.memory.map
import org.usvm.uctx
import org.usvm.withSizeSort

data class UStringLValue(val ref: UHeapRef): ULValue<UStringLValue, UStringSort> {
    override val sort: UStringSort = ref.uctx.stringSort

    override val memoryRegionId: UMemoryRegionId<UStringLValue, UStringSort> =
        UStringRegionId(ref.uctx)

    override val key: UStringLValue
        get() = this
}

data class UStringRegionId(val ctx: UContext<*>) :
    UMemoryRegionId<UStringLValue, UStringSort> {
    override val sort: UStringSort = ctx.stringSort

    override fun emptyRegion(): UStringRegion =
        UStringMemoryRegion()
}

typealias UInputStrings = USymbolicCollection<UInputStringId, UHeapRef, UStringSort>

interface UStringRegion : UMemoryRegion<UStringLValue, UStringSort>

internal class UStringMemoryRegion(
    /**
     * Strings with negative concrete heap addresses -- can be aliased by input strings
     */
    private var internedStrings: PersistentMap<UConcreteHeapAddress, UStringExpr> = persistentHashMapOf(),
    /**
     * Strings with positive concrete heap addresses -- cannot be aliased by input strings
     */
    private var allocatedStrings: PersistentMap<UConcreteHeapAddress, UStringExpr> = persistentHashMapOf(),
    private var inputStrings: UInputStrings? = null
): UStringRegion {

    private fun getAllocatedString(ref: UConcreteHeapRef): UStringExpr {
        var string = allocatedStrings[ref.address]
        if (string == null) {
            string = ref.uctx.mkEmptyString()
            allocatedStrings.put(ref.address, string)
        }
        return string
    }

    private fun getInternedString(ref: UConcreteHeapRef): UStringExpr {
        var string = internedStrings[ref.address]
        if (string == null) {
            string = ref.uctx.mkEmptyString()
            internedStrings.put(ref.address, string)
        }
        return string
    }

    private fun getInputString(ref: UHeapRef): UStringExpr {
        if (inputStrings == null)
            inputStrings = UInputStringId(ref.uctx).emptyRegion()
        return inputStrings!!.read(ref)
    }

    private fun updateInternedString(ref: UConcreteHeapRef, updated: UStringExpr, guard: UBoolExpr): UStringMemoryRegion {
        val updatedInternedStrings = internedStrings.guardedWrite(ref.address, updated, guard) {ref.uctx.mkEmptyString()}
        return UStringMemoryRegion(updatedInternedStrings, allocatedStrings, inputStrings)
    }

    private fun updateAllocatedString(ref: UConcreteHeapRef, updated: UStringExpr, guard: UBoolExpr): UStringMemoryRegion {
        val updatedAllocatedStrings = allocatedStrings.guardedWrite(ref.address, updated, guard) {ref.uctx.mkEmptyString()}
        return UStringMemoryRegion(internedStrings, updatedAllocatedStrings, inputStrings)
    }

    private fun updateInputString(ref: UHeapRef, updated: UStringExpr, guard: UBoolExpr): UStringMemoryRegion {
        val input = inputStrings ?: UInputStringId(ref.uctx).emptyRegion()
        return UStringMemoryRegion(internedStrings, allocatedStrings, input.write(ref, updated, guard))
    }

    inline fun <Sort: USort> mapString(ref: UHeapRef, mapper: (UStringExpr) -> UExpr<Sort>): UExpr<Sort> =
        ref.map({mapper(getAllocatedString(it))}, {mapper(getInternedString(it))}, {mapper(getInputString(it))})

    internal fun getString(ref: UHeapRef): UStringExpr =
        ref.map(::getAllocatedString, ::getInternedString, ::getInputString)

    override fun read(key: UStringLValue): UExpr<UStringSort> =
        getString(key.ref)

    override fun write(
        key: UStringLValue,
        value: UExpr<UStringSort>,
        guard: UBoolExpr
    ): UMemoryRegion<UStringLValue, UStringSort> = foldHeapRef(key.ref, this, guard, true, true,
        {acc, guardedRef -> acc.updateAllocatedString(guardedRef.expr, value, guardedRef.guard)},
        {acc, guardedRef -> acc.updateInternedString(guardedRef.expr, value, guardedRef.guard)},
        {acc, guardedRef -> acc.updateInputString(guardedRef.expr, value, guardedRef.guard)})
}
