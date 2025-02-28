package org.usvm.machine.interpreter

import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFieldSubSignature
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.getOrDefault
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.tctx
import org.usvm.machine.state.TsState
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.guardedWrite
import org.usvm.sampleUValue

data class TsStaticFieldLValue<Sort : USort>(
    val field: EtsFieldSignature,
    override val sort: Sort,
) : ULValue<TsStaticFieldLValue<Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<TsStaticFieldLValue<Sort>, Sort> = TsStaticFieldRegionId(sort)

    override val key: TsStaticFieldLValue<Sort>
        get() = this
}

data class TsStaticFieldRegionId<Sort : USort>(
    override val sort: Sort,
) : UMemoryRegionId<TsStaticFieldLValue<Sort>, Sort> {
    override fun emptyRegion(): UMemoryRegion<TsStaticFieldLValue<Sort>, Sort> = TsStaticFieldsMemoryRegion(sort)
}

internal class TsStaticFieldsMemoryRegion<Sort : USort>(
    private val sort: Sort,
    private var fieldValuesByClass: UPersistentHashMap<EtsClassSignature, UPersistentHashMap<EtsFieldSignature, UExpr<Sort>>> =
        persistentHashMapOf(),
) : UMemoryRegion<TsStaticFieldLValue<Sort>, Sort> {
    override fun read(key: TsStaticFieldLValue<Sort>): UExpr<Sort> {
        val field = key.field
        return fieldValuesByClass[field.enclosingClass]?.get(field)
            ?: sort.tctx.mkStaticFieldReading(key.memoryRegionId as TsStaticFieldRegionId, field, sort)
    }

    override fun write(
        key: TsStaticFieldLValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<TsStaticFieldLValue<Sort>, Sort> {
        val field = key.field
        val enclosingClass = field.enclosingClass
        val classFields = fieldValuesByClass.getOrDefault(enclosingClass, persistentHashMapOf())

        val newFieldValues = classFields.guardedWrite(key.field, value, guard, ownership) { key.sort.sampleUValue() }
        val newFieldsByClass = fieldValuesByClass.put(enclosingClass, newFieldValues, ownership)

        return TsStaticFieldsMemoryRegion(sort, newFieldsByClass)
    }
}

internal fun TsState.isInitialized(clazz: EtsClass): Boolean {
    val initializedFlag = ctx.staticFieldsInitializedFlag(clazz)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markInitialized(clazz: EtsClass) {
    val initializedFlag = ctx.staticFieldsInitializedFlag(clazz)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun TsContext.staticFieldsInitializedFlag(clazz: EtsClass): TsStaticFieldLValue<UBoolSort> {
    val field = EtsFieldSignature(
        enclosingClass = clazz.signature,
        sub = staticFieldsInitializedFlagField,
    )
    return TsStaticFieldLValue(field, boolSort)
}

private val staticFieldsInitializedFlagField: EtsFieldSubSignature by lazy {
    EtsFieldSubSignature(
        name = "__initialized__",
        type = EtsBooleanType,
    )
}
