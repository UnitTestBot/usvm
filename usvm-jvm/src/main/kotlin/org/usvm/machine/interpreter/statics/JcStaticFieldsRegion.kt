package org.usvm.machine.interpreter.statics

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.cfg.util.isPrimitive
import org.jacodb.impl.types.FieldInfo
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.jctx
import org.usvm.machine.state.JcState
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.guardedWrite
import org.usvm.sampleUValue
import java.lang.reflect.Modifier

data class JcStaticFieldLValue<Sort : USort>(
    val field: JcField,
    override val sort: Sort,
) : ULValue<JcStaticFieldLValue<Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<JcStaticFieldLValue<Sort>, Sort> = JcStaticFieldRegionId(sort)

    override val key: JcStaticFieldLValue<Sort>
        get() = this
}

data class JcStaticFieldRegionId<Sort : USort>(
    override val sort: Sort,
) : UMemoryRegionId<JcStaticFieldLValue<Sort>, Sort> {
    override fun emptyRegion(): UMemoryRegion<JcStaticFieldLValue<Sort>, Sort> = JcStaticFieldsMemoryRegion(sort)
}

internal class JcStaticFieldsMemoryRegion<Sort : USort>(
    private val sort: Sort,
    private var fieldValuesByClass: PersistentMap<JcClassOrInterface, PersistentMap<JcField, UExpr<Sort>>> = persistentHashMapOf(),
    private var initialStatics: PersistentList<JcField> = persistentListOf()
) : UMemoryRegion<JcStaticFieldLValue<Sort>, Sort> {
    val mutableStaticFields: List<JcField>
        get() = initialStatics

    override fun read(key: JcStaticFieldLValue<Sort>): UExpr<Sort> {
        val field = key.field
        return fieldValuesByClass[field.enclosingClass]?.get(field)
            ?: sort.jctx.mkStaticFieldReading(key.memoryRegionId as JcStaticFieldRegionId, field, sort)
    }

    override fun write(
        key: JcStaticFieldLValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ): UMemoryRegion<JcStaticFieldLValue<Sort>, Sort> {
        val field = key.field

        val enclosingClass = field.enclosingClass
        if (enclosingClass !in fieldValuesByClass) {
            fieldValuesByClass = fieldValuesByClass.put(enclosingClass, persistentHashMapOf())
        }

        val newFieldValues = fieldValuesByClass
            .getValue(enclosingClass)
            .guardedWrite(key.field, value, guard) { key.sort.sampleUValue() }
        val newFieldsByClass = fieldValuesByClass.put(enclosingClass, newFieldValues)

        return JcStaticFieldsMemoryRegion(sort, newFieldsByClass, initialStatics)
    }

    fun mutatePrimitiveStaticFieldValuesToSymbolic(enclosingClass: JcClassOrInterface) {
        val staticFields = fieldValuesByClass[enclosingClass] ?: return

        val staticsToRemove = staticFields
            .keys
            .filter { fieldShouldBeSymbolic(it) }

        initialStatics = initialStatics.addAll(staticsToRemove)

        // Remove concrete fields from the region
        val updatedStaticFields = staticsToRemove.fold(staticFields) { acc, field ->
            acc.remove(field)
        }
        fieldValuesByClass = fieldValuesByClass.put(enclosingClass, updatedStaticFields)
    }

    companion object {
        val fieldShouldBeSymbolic: (JcField) -> Boolean = { field ->
            field.type.isPrimitive
                    && !field.isFinal
                    && field.name != staticFieldsInitializedFlagField.name
                    && !field.enclosingClass.declaration.location.isRuntime
        }
    }
}

internal fun extractInitialStatics(ctx: JcContext, memory: UReadOnlyMemory<JcType>): List<JcField> =
    ctx.primitiveTypes.flatMap {
        val sort = ctx.typeToSort(it)

        if (sort == ctx.voidSort) return@flatMap emptyList()

        val regionId = JcStaticFieldRegionId(sort)
        val region = memory.getRegion(regionId) as JcStaticFieldsMemoryRegion<*>

        region.mutableStaticFields
    }

internal fun JcState.isInitialized(type: JcRefType): Boolean {
    val initializedFlag = staticFieldsInitializedFlag(ctx, type)
    return memory.read(initializedFlag).isTrue
}

internal fun JcState.markAsInitialized(type: JcRefType) {
    val initializedFlag = staticFieldsInitializedFlag(ctx, type)
    memory.write(initializedFlag, rvalue = ctx.trueExpr, guard = ctx.trueExpr)
}

private fun staticFieldsInitializedFlag(ctx: JcContext, type: JcRefType): JcStaticFieldLValue<UBoolSort> =
    JcStaticFieldLValue(
        sort = ctx.booleanSort,
        field = JcFieldImpl(type.jcClass, staticFieldsInitializedFlagField),
    )

/**
 * Synthetic field to track static field initialization state.
 * */
private val staticFieldsInitializedFlagField by lazy {
    FieldInfo(
        name = "__initialized__",
        signature = null,
        access = Modifier.FINAL or Modifier.STATIC or Modifier.PRIVATE,
        type = PredefinedPrimitives.Boolean,
        annotations = emptyList()
    )
}
