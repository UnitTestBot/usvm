package org.usvm.machine.interpreter

import io.ksmt.utils.cast
import io.ksmt.utils.mkFreshConst
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.jacodb.api.JcRefType
import org.jacodb.api.JcField
import org.jacodb.api.ext.toType
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcState
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.guardedWrite
import org.usvm.sampleUValue

data class JcStaticFieldLValue<Sort : USort>(
    val field: JcField,
    val ctx: JcContext,
    override val sort: Sort,
) : ULValue<JcStaticFieldLValue<Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<JcStaticFieldLValue<Sort>, Sort> =
        // Do not use the presented sort here - we always write and read using voidSort
        JcStaticFieldRegionId(field.enclosingClass.toType(), ctx.voidSort).cast()

    override val key: JcStaticFieldLValue<Sort>
        get() = this
}

data class JcStaticFieldRegionId(
    private val type: JcRefType,
    override val sort: USort, // this sort does not matter as it is not used
) : UMemoryRegionId<JcStaticFieldLValue<USort>, USort> {
    override fun emptyRegion(): UMemoryRegion<JcStaticFieldLValue<USort>, USort> = JcStaticFieldsMemoryRegion(type)
}

internal class JcStaticFieldsMemoryRegion(
    private val type: JcRefType,
    private var classFieldValues: PersistentMap<JcField, UExpr<USort>> = persistentHashMapOf(),
) : UMemoryRegion<JcStaticFieldLValue<USort>, USort> {
    override fun read(key: JcStaticFieldLValue<USort>): UExpr<USort> = classFieldValues[key.field] ?: key.sort.sampleUValue()

    override fun write(
        key: JcStaticFieldLValue<USort>,
        value: UExpr<USort>,
        guard: UBoolExpr
    ): UMemoryRegion<JcStaticFieldLValue<USort>, USort> {
        val newFieldValues = classFieldValues.guardedWrite(key.field, value, guard) { key.sort.sampleUValue() }

        return JcStaticFieldsMemoryRegion(type, newFieldValues)
    }

    fun mutatePrimitiveFieldValuesToSymbolic(state: JcState) {
        val mutablePrimitiveStaticFieldsToSymbolicValues = classFieldValues.entries.filter { (field, _) ->
            field.type.isPrimitive && !field.isFinal
        }.associate { (field, value) ->
            field to state.makeSymbolicPrimitive(value.sort)
        }

        // Mutate field values in place because we do not have any LValue to write on it into memory
        classFieldValues = classFieldValues.putAll(mutablePrimitiveStaticFieldsToSymbolicValues)
    }
}
