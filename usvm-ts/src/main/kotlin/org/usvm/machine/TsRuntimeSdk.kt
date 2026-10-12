package org.usvm.machine

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.usvm.util.EtsHierarchy

private val runtimeSdkFile = EtsHierarchy.OBJECT_CLASS.signature.file

/**
 * Minimal prototype identities for the standard Object, Function and Array constructors.
 * These declarations support runtime membership checks, not execution of their standard-library APIs.
 * Preserve explicitly loaded SDK declarations; add only the missing core identities. Identity-only declarations
 * are interfaces so the input generator does not invent ordinary instances for functions and arrays, which use
 * their existing dedicated runtime representations.
 */
internal fun EtsScene.withCoreRuntimeSdk(): EtsScene {
    val objectSignature = sdkClasses.singleOrNull { it.name == "Object" }?.signature
        ?: EtsHierarchy.OBJECT_CLASS.signature
    val missingClasses = listOf("Object", "Function", "Array")
        .filter { name -> sdkClasses.none { it.name == name } }
        .map { name ->
            EtsClassImpl(
                signature = EtsClassSignature(name = name, file = runtimeSdkFile),
                fields = emptyList(),
                methods = emptyList(),
                category = EtsClassCategory.INTERFACE,
                superClass = if (name == "Object") null else objectSignature,
            )
        }
    if (missingClasses.isEmpty()) return this

    val coreSdk = EtsFile(signature = runtimeSdkFile, classes = missingClasses, namespaces = emptyList())
    return EtsScene(projectFiles = projectFiles, sdkFiles = sdkFiles + coreSdk, projectName = projectName)
}

/** An undeclared frontend local denotes a standard-library constructor, rather than a project binding. */
internal fun EtsScene.sdkConstructorForGlobal(local: EtsLocal): EtsClass? =
    sdkClasses.singleOrNull { it.name == local.name }

internal fun EtsScene.runtimeFunctionType(): EtsClassType {
    val functionClass = sdkClasses.singleOrNull { it.name == "Function" }
        ?: throw UnsupportedOperationException("SDK Function identity is ambiguous or unavailable")
    return EtsClassType(functionClass.signature)
}
