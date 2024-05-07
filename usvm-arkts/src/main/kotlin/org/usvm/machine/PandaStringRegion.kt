package org.usvm.machine

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.jacodb.panda.dynamic.api.PandaField
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.guardedWrite


data class PandaStringFieldLValue(
    val addr: UHeapRef,
    val field: PandaField,
    override val sort: PandaStringSort
) : ULValue<PandaStringFieldLValue, PandaStringSort> {
    override val memoryRegionId: UMemoryRegionId<PandaStringFieldLValue, PandaStringSort>
        get() = PandaStringFieldRegionId(sort)

    override val key: PandaStringFieldLValue
        get() = this

}

data class PandaStringFieldRegionId(
    override val sort: PandaStringSort
) : UMemoryRegionId<PandaStringFieldLValue, PandaStringSort> {
    override fun emptyRegion(): UMemoryRegion<PandaStringFieldLValue, PandaStringSort> =
        PandaStringFieldMemoryRegion()
}

internal class PandaStringFieldMemoryRegion(
    private var stringConstants: PersistentMap<UConcreteHeapRef, UExpr<PandaStringSort>> = persistentHashMapOf(),
    private var otherStrings: PersistentMap<UHeapRef, UExpr<PandaStringSort>> = persistentHashMapOf()
) : UMemoryRegion<PandaStringFieldLValue, PandaStringSort> {
    override fun read(key: PandaStringFieldLValue): UExpr<PandaStringSort> {
        val heapRef = key.addr
        if (heapRef is UConcreteHeapRef) {
            return stringConstants.getValue(heapRef)
        }
        // TODO error
        return PandaConcreteString(key.addr.pctx, "TODO")
    }

    override fun write(
        key: PandaStringFieldLValue,
        value: UExpr<PandaStringSort>,
        guard: UBoolExpr,
    ): UMemoryRegion<PandaStringFieldLValue, PandaStringSort> {
        if (key.addr is UConcreteHeapRef) {
            val newStringConstants = stringConstants.guardedWrite(key.addr, value, guard) { TODO() }
            return PandaStringFieldMemoryRegion(newStringConstants)
        }

        val newStrings = otherStrings.guardedWrite(key.addr, value, guard) { TODO() }
        return PandaStringFieldMemoryRegion(stringConstants, newStrings)
    }

}
