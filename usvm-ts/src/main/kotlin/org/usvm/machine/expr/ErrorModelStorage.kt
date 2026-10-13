package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TS_TYPE_ERROR_TYPE
import org.usvm.machine.TsContext
import org.usvm.machine.call.intrinsic.TsErrorEtsIrModelFamily
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.types.singleOrNull
import org.usvm.util.boxArrayValue

internal val builtInErrorSignature = EtsClassSignature.UNKNOWN.copy(name = "Error")

/** Matches exact SDK identities and the interpreter's own TypeError, excluding source class shadows. */
internal fun EtsType.isModeledErrorType(): Boolean =
    this == EtsClassType(signature = builtInErrorSignature) ||
        this == EtsClassType(signature = EtsClassSignature.UNKNOWN.copy(name = "TypeError")) ||
        this == TS_TYPE_ERROR_TYPE

internal const val ERROR_NAME_STORAGE_FIELD = "__usvmErrorName"
internal const val ERROR_MESSAGE_STORAGE_FIELD = "__usvmErrorMessage"

internal fun EtsFieldSignature.isErrorModelStorageDefinitionField(): Boolean =
    enclosingClass.name == "ErrorValue" &&
        enclosingClass.file.fileName == "ErrorModels.ts" &&
        (name == ERROR_NAME_STORAGE_FIELD || name == ERROR_MESSAGE_STORAGE_FIELD)

internal fun EtsFieldSignature.isUnresolvedErrorField(): Boolean =
    enclosingClass == builtInErrorSignature && (name == "name" || name == "message")

internal enum class ErrorStorageField { NAME, MESSAGE }

internal fun <Sort : USort> mkErrorModelStorageLValue(
    sort: Sort,
    ref: UHeapRef,
    key: ErrorStorageField,
): UFieldLValue<ErrorStorageField, Sort> = UFieldLValue(sort, ref, key)

private fun EtsFieldSignature.errorModelDefinitionStorageField(): ErrorStorageField? {
    if (!isErrorModelStorageDefinitionField()) return null

    return when (name) {
        ERROR_NAME_STORAGE_FIELD -> ErrorStorageField.NAME
        ERROR_MESSAGE_STORAGE_FIELD -> ErrorStorageField.MESSAGE
        else -> null
    }
}

internal fun EtsFieldSignature.errorModelStorageField(): ErrorStorageField? {
    if (enclosingClass != builtInErrorSignature && enclosingClass != EtsClassSignature.UNKNOWN) {
        return null
    }

    return when (name) {
        "name" -> ErrorStorageField.NAME
        "message" -> ErrorStorageField.MESSAGE
        else -> null
    }
}

/** Only unresolved SDK Error fields need the unqualified declaration lookup. */
internal fun EtsFieldSignature.runtimeFieldSignature(): EtsFieldSignature =
    if (isUnresolvedErrorField()) copy(enclosingClass = EtsClassSignature.UNKNOWN) else this

private fun TsContext.resolveErrorStorageField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
): ErrorStorageField? {
    val definitionStorageField = field.errorModelDefinitionStorageField().takeIf {
        applicationAndSdkClasses.none { owner -> owner.signature == field.enclosingClass } &&
            TsErrorEtsIrModelFamily.ownsStorageDefinitionField(field)
    }
    val candidateStorageField = definitionStorageField ?: field.errorModelStorageField() ?: return null
    val concreteInstance = instance as? UConcreteHeapRef ?: return null
    val runtimeType = scope.calcOnState { memory.typeStreamOf(concreteInstance).singleOrNull() }

    return candidateStorageField.takeIf { runtimeType == EtsClassType(signature = builtInErrorSignature) }
}

internal fun TsContext.readModeledErrorField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
): UExpr<*>? {
    val storageField = resolveErrorStorageField(scope, instance, field) ?: return null

    return scope.calcOnState {
        memory.read(mkErrorModelStorageLValue(addressSort, instance, storageField))
    }
}

internal fun TsContext.writeModeledErrorField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
    value: UExpr<*>,
): Boolean {
    val storageField = resolveErrorStorageField(scope, instance, field) ?: return false
    val payload = boxArrayValue(scope, value)

    scope.doWithState {
        memory.write(
            mkErrorModelStorageLValue(addressSort, instance, storageField),
            payload.asExpr(addressSort),
            guard = trueExpr,
        )
        memory.write(deletedFieldLValue(instance, field), falseExpr, guard = trueExpr)
        writtenConcreteFields += instance to field.name
    }
    return true
}

internal fun TsContext.normalizeThrownValue(scope: TsStepScope, exception: UExpr<*>): UExpr<*>? {
    var value = exception
    while (value.sort == addressSort) {
        val reference = resolveHeapRef(scope, value.asExpr(addressSort)) ?: return null
        if (!reference.isFakeObject()) return reference

        val type = reference.getFakeType(scope)
        val booleanKind = scope.calcOnState { models.first().eval(type.boolTypeExpr).isTrue }
        val numberKind = scope.calcOnState { models.first().eval(type.fpTypeExpr).isTrue }
        val selectedKind = when {
            booleanKind -> type.boolTypeExpr
            numberKind -> type.fpTypeExpr
            else -> type.refTypeExpr
        }
        scope.fork(selectedKind) ?: return null
        value = when {
            booleanKind -> reference.extractBool(scope)
            numberKind -> reference.extractFp(scope)
            else -> reference.extractRef(scope)
        }
    }
    return value
}

internal fun TsContext.thrownValueType(scope: TsStepScope, exception: UExpr<*>): EtsType =
    when (exception.sort) {
        addressSort -> {
            val reference = exception as? UConcreteHeapRef
            val runtimeType = reference?.let { ref ->
                scope.calcOnState { memory.typeStreamOf(ref).singleOrNull() }
            }
            runtimeType?.takeIf { type -> type.isModeledErrorType() } ?: EtsStringType
        }
        fp64Sort -> EtsNumberType
        boolSort -> EtsBooleanType
        else -> EtsStringType
    }
