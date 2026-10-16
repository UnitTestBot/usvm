package org.usvm.machine

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.etsIrSerializerScript
import org.jacodb.ets.utils.generateEtsIR
import org.usvm.util.EtsHierarchy
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream

private val coreRuntimeClassNames = setOf("Object", "Function", "Array", "Date", "Math")

// JacoDB ships the matching TypeScript lib*.d.ts next to its frontend. Single-file conversion supports
// declarations, while project mode deliberately skips them. Cache the DTO, not mutable scene-owned classes.
private val coreRuntimeSdk: EtsFileDto by lazy {
    val library = etsIrSerializerScript(EtsIrProvider.TS_FRONTEND).resolveSibling("lib.es5.d.ts")
    val ir = generateEtsIR(library, loadEntrypoints = false, provider = EtsIrProvider.TS_FRONTEND)
    try {
        val file = ir.inputStream().use { EtsFileDto.loadFromJson(it) }
        // The ES5 library augments Date in several interface declarations with one identity.
        val runtimeClasses = file.classes.filter { it.signature.name in coreRuntimeClassNames }
        val mergedClasses = runtimeClasses.groupBy { it.signature }.values.map { declarations ->
            val first = declarations.first()
            // Date supplies nominal identity only. Its APIs retain their existing unknown-call boundary.
            val methods = if (first.signature.name == "Date") {
                emptyList()
            } else {
                declarations.flatMap { it.methods }.distinctBy { it.signature }
            }
            first.copy(
                fields = declarations.flatMap { it.fields }.distinctBy { it.signature },
                methods = methods,
            )
        }
        file.copy(
            classes = mergedClasses,
            namespaces = emptyList(),
        )
    } finally {
        ir.deleteIfExists()
    }
}

/** Standard Function and Array declarations use dedicated values, not ordinary class-instance witnesses. */
internal fun EtsClassType.isCoreRuntimePrototypeIdentity(): Boolean =
    signature.file == EtsHierarchy.OBJECT_CLASS.signature.file &&
        signature.name in setOf("Function", "Array")

/**
 * Load Object, Function, Array, Date and Math from the standard declarations bundled in the pinned JacoDB dependency.
 * Keep existing Object/Function/Array/Math members and add nominal Date identity. Preserve SDK declarations
 * supplied by the caller. Date APIs continue through the existing unknown-call and semantic-model boundary.
 */
internal fun EtsScene.withCoreRuntimeSdk(): EtsScene {
    val missingNames = coreRuntimeClassNames - sdkClasses.map { it.name }.toSet()
    if (missingNames.isEmpty()) return this

    val library = coreRuntimeSdk.toEtsFile()
    val classes = library.classes.filter { it.name in missingNames }
    check(classes.map { it.name }.toSet() == missingNames) { "Core TypeScript SDK declarations are missing" }
    val file = EtsFile(signature = library.signature, classes = classes, namespaces = emptyList())

    return EtsScene(projectFiles = projectFiles, sdkFiles = sdkFiles + file, projectName = projectName)
}

/** An undeclared frontend local denotes a standard-library constructor, rather than a project binding. */
internal fun EtsScene.sdkConstructorForGlobal(local: EtsLocal): EtsClass? =
    sdkClasses.singleOrNull { it.name == local.name && it.name != "Math" }

internal fun EtsScene.runtimeFunctionType(): EtsClassType {
    val functionClass = sdkClasses.singleOrNull { it.name == "Function" }
        ?: throw UnsupportedOperationException("SDK Function identity is ambiguous or unavailable")
    return EtsClassType(functionClass.signature)
}
