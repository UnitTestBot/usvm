package org.usvm.memory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.sampleUValue

data class GoPointerLValue<Sort : USort>(
    val ref: UHeapRef,
    override val sort: Sort,
) : ULValue<GoPointerLValue<Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<GoPointerLValue<Sort>, Sort>
        get() = GoPointerRegionId(sort)

    override val key: GoPointerLValue<Sort>
        get() = this

}

data class GoPointerRegionId<Sort : USort>(
    override val sort: Sort,
) : UMemoryRegionId<GoPointerLValue<Sort>, Sort> {
    override fun emptyRegion(): UMemoryRegion<GoPointerLValue<Sort>, Sort> {
        return GoPointerMemoryRegion()
    }
}

interface GoPointerRegion<Sort : USort> : UMemoryRegion<GoPointerLValue<Sort>, Sort>

class GoPointerMemoryRegion<Sort : USort>(
    private var pointers: PersistentMap<UHeapRef, UExpr<Sort>> = persistentHashMapOf(),
) : GoPointerRegion<Sort> {
    override fun read(key: GoPointerLValue<Sort>): UExpr<Sort> {
        return pointers[key.ref] ?: key.sort.sampleUValue()
    }

    override fun write(
        key: GoPointerLValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<GoPointerLValue<Sort>, Sort> {
        val newPointers = pointers.guardedWrite(key.ref, value, guard) { key.sort.sampleUValue() }

        return GoPointerMemoryRegion(newPointers)
    }
}