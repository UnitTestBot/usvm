package org.usvm.machine

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.usvm.util.EtsHierarchy

private val runtimeSdkFile = EtsHierarchy.OBJECT_CLASS.signature.file
private val runtimePrototypeFile = EtsFileSignature(projectName = "usvm-runtime-sdk", fileName = "PrototypeIdentities")

/** Synthetic Function and Array identities cannot be materialized as ordinary class instances. */
internal fun EtsClassType.isCoreRuntimePrototypeIdentity(): Boolean = signature.file == runtimePrototypeFile

/**
 * Minimal prototype identities for the standard Object, Function and Array constructors.
 * These declarations support runtime membership checks, not execution of their standard-library APIs.
 * Preserve explicitly loaded SDK declarations; add only the missing core identities. Synthetic Function and Array
 * declarations have a reserved signature and are excluded from instantiable type streams: functions and arrays
 * use their existing dedicated runtime representations. Object remains an ordinary object input candidate.
 */
internal fun EtsScene.withCoreRuntimeSdk(): EtsScene {
    val objectSignature = sdkClasses.singleOrNull { it.name == "Object" }?.signature
        ?: EtsHierarchy.OBJECT_CLASS.signature
    val missingClasses = listOf("Object", "Function", "Array")
        .filter { name -> sdkClasses.none { it.name == name } }
        .map { name ->
            EtsClassImpl(
                signature = EtsClassSignature(
                    name = name,
                    file = if (name == "Object") runtimeSdkFile else runtimePrototypeFile,
                ),
                fields = emptyList(),
                methods = emptyList(),
                category = EtsClassCategory.INTERFACE,
                superClass = if (name == "Object") null else objectSignature,
            )
        }
    if (missingClasses.isEmpty()) return this

    val coreSdkFiles = missingClasses.groupBy { it.signature.file }.map { (file, classes) ->
        EtsFile(signature = file, classes = classes, namespaces = emptyList())
    }
    return EtsScene(projectFiles = projectFiles, sdkFiles = sdkFiles + coreSdkFiles, projectName = projectName)
}

/** An undeclared frontend local denotes a standard-library constructor, rather than a project binding. */
internal fun EtsScene.sdkConstructorForGlobal(local: EtsLocal): EtsClass? =
    sdkClasses.singleOrNull { it.name == local.name }

internal fun EtsScene.runtimeFunctionType(): EtsClassType {
    val functionClass = sdkClasses.singleOrNull { it.name == "Function" }
        ?: throw UnsupportedOperationException("SDK Function identity is ambiguous or unavailable")
    return EtsClassType(functionClass.signature)
}
