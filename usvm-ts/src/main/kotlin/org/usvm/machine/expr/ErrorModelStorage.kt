package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature

internal val builtInErrorSignature = EtsClassSignature.UNKNOWN.copy(name = "Error")

internal const val ERROR_NAME_STORAGE_FIELD = "__usvmErrorName"
internal const val ERROR_MESSAGE_STORAGE_FIELD = "__usvmErrorMessage"

internal fun EtsFieldSignature.isErrorModelStorageDefinitionField(): Boolean =
    enclosingClass.name == "ErrorValue" &&
        enclosingClass.file.fileName == "ErrorModels.ts" &&
        (name == ERROR_NAME_STORAGE_FIELD || name == ERROR_MESSAGE_STORAGE_FIELD)

internal fun EtsFieldSignature.isUnresolvedErrorField(): Boolean =
    enclosingClass == builtInErrorSignature && (name == "name" || name == "message")

internal fun EtsFieldSignature.errorModelStorageField(): String? {
    if (enclosingClass != builtInErrorSignature && enclosingClass != EtsClassSignature.UNKNOWN) {
        return null
    }

    return when (name) {
        "name" -> ERROR_NAME_STORAGE_FIELD
        "message" -> ERROR_MESSAGE_STORAGE_FIELD
        else -> null
    }
}
