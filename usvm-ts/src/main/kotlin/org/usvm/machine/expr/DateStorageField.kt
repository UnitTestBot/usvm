package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.TsContext
import org.usvm.machine.call.intrinsic.TsDateEtsIrModelFamily
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.types.singleOrNull

internal enum class DateStorageField { TIMESTAMP }

internal fun TsContext.isDateModelTimestamp(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
): Boolean {
    val looksLikeStorageDefinition = field.name == "timestamp" && field.enclosingClass.name == "DateValue"
    if (!looksLikeStorageDefinition || instance !is UConcreteHeapRef) return false

    val applicationOwnsDefinition = applicationAndSdkClasses.any { owner -> owner.signature == field.enclosingClass }
    if (applicationOwnsDefinition || !TsDateEtsIrModelFamily.ownsStorageDefinitionField(field)) return false

    val runtimeType = scope.calcOnState { memory.typeStreamOf(instance).singleOrNull() }
    val builtinDateType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "Date"))
    return runtimeType == builtinDateType
}

internal fun TsContext.dateModelTimestampLValue(instance: UHeapRef) =
    UFieldLValue(fp64Sort, instance, DateStorageField.TIMESTAMP)
