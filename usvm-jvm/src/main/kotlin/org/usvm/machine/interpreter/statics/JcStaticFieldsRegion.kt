package org.usvm.machine.interpreter.statics

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.state.JcState
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.guardedWrite
import org.usvm.sampleUValue

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
) : UMemoryRegion<JcStaticFieldLValue<Sort>, Sort> {
    val mutableStaticFields: List<JcField>
        get() = fieldValuesByClass.values.flatMap { it.keys }.filter(filterPredicate)

    override fun read(key: JcStaticFieldLValue<Sort>): UExpr<Sort> {
        val field = key.field

        // TODO probably, reading by absent class or field must be prohibited, however, at the moment,
        //      error here leads to unexpected errors during analysis. This happen because
        //      we make this reading before verifying if we have to initialize a clinit section.
        //      For more details, replace `sampleUValue` with an error and run ShortWrapperTest.primitiveToWrapperTest
        return fieldValuesByClass[field.enclosingClass]?.get(field) ?: key.sort.sampleUValue()
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

        return JcStaticFieldsMemoryRegion(sort, newFieldsByClass)
    }

    fun mutatePrimitiveStaticFieldValuesToSymbolic(state: JcState, enclosingClass: JcClassOrInterface) {
        val staticFields = fieldValuesByClass[enclosingClass] ?: return

        val mutablePrimitiveStaticFieldsToSymbolicValues = staticFields
            .entries
            .filter { (field, _) -> filterPredicate(field) }
            .associate { (field, value) ->
                val lvalue = JcStaticFieldLValue(field, value.sort)
                val regionId = lvalue.memoryRegionId as JcStaticFieldRegionId
                val symbol = state.ctx.mkStaticFieldReading(regionId, field, value.sort)

                state.memory.write(lvalue, symbol, guard = state.ctx.trueExpr)

                field to state.memory.read(lvalue)
            }

        // Mutate field values in place because we do not have any LValue to write on it into memory
        val updatedStaticFields = staticFields.putAll(mutablePrimitiveStaticFieldsToSymbolicValues)
        fieldValuesByClass = fieldValuesByClass.put(enclosingClass, updatedStaticFields)
    }

    companion object {
        val filterPredicate: (JcField) -> Boolean = { field -> field.type.isPrimitive && !field.isFinal }
    }
}
