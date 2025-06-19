package org.usvm.machine.interpreter

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId

object JcStringInterningRegionId : UMemoryRegionId<Nothing, Nothing> {
    override val sort: Nothing
        get() = throw IllegalStateException("JcStringInterningRegionId.sort is unreachable")

    override fun emptyRegion(): UMemoryRegion<Nothing, Nothing> = JcStringInterningRegion()
}

internal class JcStringInterningRegion(
    private val interningPool: PersistentMap<String, UConcreteHeapRef> = persistentMapOf()
): UMemoryRegion<Nothing, Nothing> {

    fun getOrPut(string: String, defaultValue: () -> UConcreteHeapRef): Pair<UConcreteHeapRef, JcStringInterningRegion> {
        val address = interningPool[string]
        if (address != null)
            return address to this

        val newAddress = defaultValue()
        val newPool = interningPool.put(string, newAddress)
        return newAddress to JcStringInterningRegion(newPool)
    }

    override fun read(key: Nothing): UExpr<Nothing> {
        throw IllegalStateException("JcStringInterningRegion.read is unreachable")
    }

    override fun write(key: Nothing, value: UExpr<Nothing>, guard: UBoolExpr, ownership: MutabilityOwnership): UMemoryRegion<Nothing, Nothing> {
        throw IllegalStateException("JcStringInterningRegion.write is unreachable")
    }
}
