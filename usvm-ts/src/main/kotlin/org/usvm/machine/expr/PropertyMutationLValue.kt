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

internal enum class PropertyMutationKind {
    DELETED,
    WRITTEN,
}

/** Execution-time mutation markers, separate from initial presence and stored values. */
internal data class PropertyMutationLValue(
    override val sort: UBoolSort,
    override val key: UHeapRef,
    val name: String,
    val kind: PropertyMutationKind,
) : ULValue<UHeapRef, UBoolSort> {
    override val memoryRegionId: UMemoryRegionId<UHeapRef, UBoolSort> = PropertyMutationRegionId(name, kind, sort)
}

private data class PropertyMutationRegionId(
    val name: String,
    val kind: PropertyMutationKind,
    override val sort: UBoolSort,
) : UMemoryRegionId<UHeapRef, UBoolSort> {
    override fun emptyRegion(): UMemoryRegion<UHeapRef, UBoolSort> = PropertyMutationRegion(sort)
}

/** No write or deletion has occurred before execution, even on a symbolic input reference. */
private class PropertyMutationRegion(
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
    ): UMemoryRegion<UHeapRef, UBoolSort> = PropertyMutationRegion(sort, updates.write(key, value, guard))
}

internal fun TsContext.deletedFieldLValue(
    instance: UHeapRef,
    field: EtsFieldSignature,
): PropertyMutationLValue = deletedFieldLValue(instance, field.name)

internal fun TsContext.deletedFieldLValue(
    instance: UHeapRef,
    fieldName: String,
): PropertyMutationLValue = PropertyMutationLValue(boolSort, instance, fieldName, kind = PropertyMutationKind.DELETED)

internal fun TsContext.writtenPropertyLValue(
    instance: UHeapRef,
    fieldName: String,
): PropertyMutationLValue = PropertyMutationLValue(boolSort, instance, fieldName, kind = PropertyMutationKind.WRITTEN)
