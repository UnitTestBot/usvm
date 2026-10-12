package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsLocal
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.TsContext
import org.usvm.machine.call.intrinsic.TsErrorEtsIrModelFamily
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.types.singleOrNull
import org.usvm.util.EtsHierarchy
import org.usvm.util.TsResolutionResult
import org.usvm.util.resolveEtsField

internal val builtInErrorSignature = EtsClassSignature.UNKNOWN.copy(name = "Error")

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

internal data class ModelStorageFieldResolution(
    val storageField: ErrorStorageField?,
    val etsField: TsResolutionResult<EtsField>,
    val isModelStorageField: Boolean,
)

internal fun TsContext.resolveModelStorageField(
    scope: TsStepScope,
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): ModelStorageFieldResolution {
    val definitionStorageField = field.errorModelDefinitionStorageField().takeIf {
        applicationAndSdkClasses.none { owner -> owner.signature == field.enclosingClass } &&
            TsErrorEtsIrModelFamily.ownsStorageDefinitionField(field)
    }
    val candidateStorageField = definitionStorageField ?: field.errorModelStorageField()
    val runtimeType = (instance as? UConcreteHeapRef)
        ?.takeIf { candidateStorageField != null }
        ?.let { concreteInstance ->
            scope.calcOnState { memory.typeStreamOf(concreteInstance).singleOrNull() }
        }
    val storageField = candidateStorageField.takeIf {
        runtimeType == EtsClassType(signature = builtInErrorSignature)
    }
    val etsField = when {
        storageField != null -> TsResolutionResult.Empty
        field.isUnresolvedErrorField() -> resolveEtsField(
            instance = instanceLocal,
            field = field.copy(enclosingClass = EtsClassSignature.UNKNOWN),
            hierarchy = hierarchy,
        )
        else -> resolveEtsField(instanceLocal, field, hierarchy)
    }

    return ModelStorageFieldResolution(
        storageField = storageField,
        etsField = etsField,
        isModelStorageField = storageField != null,
    )
}
