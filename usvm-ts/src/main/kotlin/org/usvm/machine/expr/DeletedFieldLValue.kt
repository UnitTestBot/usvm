package org.usvm.machine.expr

import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TsContext
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.USymbolicCollectionUpdates
import org.usvm.memory.UUpdateNode
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx

/** Separate from value storage: a deleted property has no value in any sort. */
internal data class DeletedFieldLValue(
    override val sort: UBoolSort,
    override val key: UHeapRef,
    val name: String,
) : ULValue<UHeapRef, UBoolSort> {
    override val memoryRegionId: UMemoryRegionId<UHeapRef, UBoolSort> = DeletedFieldRegionId(name, sort)
}

private data class DeletedFieldRegionId(
    val name: String,
    override val sort: UBoolSort,
) : UMemoryRegionId<UHeapRef, UBoolSort> {
    override fun emptyRegion(): UMemoryRegion<UHeapRef, UBoolSort> = DeletedFieldRegion(sort)
}

/** The marker records execution events, so input references also start with no deletion. */
private class DeletedFieldRegion(
    private val sort: UBoolSort,
    private val updates: USymbolicCollectionUpdates<UHeapRef, UBoolSort> = UFlatUpdates(UHeapRefKeyInfo),
) : UMemoryRegion<UHeapRef, UBoolSort> {
    override fun read(key: UHeapRef): UBoolExpr {
        val ctx = sort.uctx
        val visitor = object : UMemoryUpdatesVisitor<UHeapRef, UBoolSort, UBoolExpr> {
            override fun visitSelect(result: UBoolExpr, key: UHeapRef): UBoolExpr = result

            override fun visitInitialValue(): UBoolExpr = ctx.falseExpr

            override fun visitUpdate(previous: UBoolExpr, update: UUpdateNode<UHeapRef, UBoolSort>): UBoolExpr {
                val guard = update.includesSymbolically(key, composer = null)
                val value = update.value(key, composer = null)

                return ctx.mkIte(guard, value, previous)
            }
        }

        return updates.read(key, composer = null).accept(visitor, lookupCache = hashMapOf())
    }

    override fun write(
        key: UHeapRef,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<UHeapRef, UBoolSort> = DeletedFieldRegion(sort, updates.write(key, value, guard))
}

// Reading these names after deleting an own property requires Object.prototype lookup.
internal val OBJECT_PROTOTYPE_PROPERTIES = setOf(
    "__defineGetter__",
    "__defineSetter__",
    "__lookupGetter__",
    "__lookupSetter__",
    "__proto__",
    "constructor",
    "hasOwnProperty",
    "isPrototypeOf",
    "propertyIsEnumerable",
    "toLocaleString",
    "toString",
    "valueOf",
)

internal fun TsContext.deletedFieldLValue(
    instance: UHeapRef,
    field: EtsFieldSignature,
): DeletedFieldLValue = DeletedFieldLValue(boolSort, instance, field.name)
