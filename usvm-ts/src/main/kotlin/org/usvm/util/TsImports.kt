package org.usvm.util

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import java.nio.file.Path
import kotlin.io.path.toPath

sealed class ImportResolutionResult {
    data class Success(val file: EtsFile) : ImportResolutionResult()
    data class NotFound(val reason: String) : ImportResolutionResult()
    data class Error(val exception: Exception) : ImportResolutionResult()
}

// Resolves an import path to an EtsFile within the given EtsScene.
//
// The [importPath] can be either a "system library" starting with `@`,
// or it can be a relative (or absolute) path to another file in the same scene.
// If the import path is relative, it is resolved against the [currentFile]'s directory.
// If the import path is absolute, it is resolved against the scene's root directory.
fun EtsScene.resolveImport(
    currentFile: EtsFile,
    importPath: String,
): ImportResolutionResult {
    return try {
        when {
            // System library starting with '@'
            importPath.startsWith("@") -> {
                resolveSystemLibrary(importPath)
            }

            // Absolute path starting with '/'
            importPath.startsWith("/") -> {
                resolveAbsolutePath(importPath)
            }

            // Relative path
            else -> {
                resolveRelativePath(currentFile, importPath)
            }
        }
    } catch (e: Exception) {
        ImportResolutionResult.Error(e)
    }
}

private fun EtsScene.resolveSystemLibrary(importPath: String): ImportResolutionResult {
    // Remove the '@' prefix and look for the library in SDK files
    val libraryName = importPath // .removePrefix("@")

    val foundFile = sdkFiles.find { file ->
        file.signature.fileName.equals(libraryName, ignoreCase = true) ||
            file.signature.fileName.equals("$libraryName.ts", ignoreCase = true) ||
            file.signature.fileName.equals("$libraryName.ets", ignoreCase = true) ||
            file.signature.fileName.equals("$libraryName.d.ts", ignoreCase = true)
    }

    return if (foundFile != null) {
        ImportResolutionResult.Success(foundFile)
    } else {
        ImportResolutionResult.NotFound("System library not found: $importPath")
    }
}

private fun EtsScene.resolveAbsolutePath(importPath: String): ImportResolutionResult {
    // Remove leading '/' and normalize the path
    val normalizedPath = importPath.removePrefix("/")

    val foundFile = (projectFiles + sdkFiles).find { file ->
        val fileName = file.signature.fileName
        fileName == normalizedPath ||
            fileName == "$normalizedPath.ts" ||
            fileName == "$normalizedPath.ets" ||
            fileName == "$normalizedPath.d.ts" ||
            fileName.endsWith("/$normalizedPath") ||
            fileName.endsWith("/$normalizedPath.ts") ||
            fileName.endsWith("/$normalizedPath.ets") ||
            fileName.endsWith("/$normalizedPath.d.ts")
    }

    return if (foundFile != null) {
        ImportResolutionResult.Success(foundFile)
    } else {
        ImportResolutionResult.NotFound("File not found for absolute path: $importPath")
    }
}

private fun EtsScene.resolveRelativePath(currentFile: EtsFile, importPath: String): ImportResolutionResult {
    val currentFileName = currentFile.signature.fileName
    val currentDir = if (currentFileName.contains("/")) {
        currentFileName.substringBeforeLast("/")
    } else {
        ""
    }

    // Construct the target path by resolving relative path components
    val targetPath = if (currentDir.isEmpty()) {
        normalizeRelativePath(importPath)
    } else {
        normalizeRelativePath("$currentDir/$importPath")
    }

    val foundFile = (projectFiles + sdkFiles).find { file ->
        val fileName = file.signature.fileName
        fileName == targetPath ||
            fileName == "$targetPath.ts" ||
            fileName == "$targetPath.ets" ||
            fileName == "$targetPath.d.ts" ||
            fileName == "$targetPath/index.ts" ||
            fileName == "$targetPath/index.ets" ||
            fileName == "$targetPath/index.d.ts"
    }

    return if (foundFile != null) {
        ImportResolutionResult.Success(foundFile)
    } else {
        ImportResolutionResult.NotFound("File not found for relative path: $importPath from ${currentFile.signature.fileName}")
    }
}

private fun normalizeRelativePath(path: String): String {
    val parts = path.split("/").toMutableList()
    val result = mutableListOf<String>()

    for (part in parts) {
        when (part) {
            "", "." -> continue
            ".." -> if (result.isNotEmpty()) result.removeLastOrNull()
            else -> result.add(part)
        }
    }

    return result.joinToString("/")
}

fun getResourcePathOrNull(res: String): Path? {
    require(res.startsWith("/")) { "Resource path must start with '/': '$res'" }
    return object {}::class.java.getResource(res)?.toURI()?.toPath()
}

fun getResourcePath(res: String): Path {
    return getResourcePathOrNull(res) ?: error("Resource not found: '$res'")
}

fun main() {
    val path = "/projects/Demo_Photos/source"
    val scene = run {
        val projectPath = getResourcePath(path)
        val project = loadEtsProjectAutoConvert(projectPath)
        val sdks = listOf(
            "/sdk/ohos/5.0.1.111/ets/api",
            "/sdk/ohos/5.0.1.111/ets/arkts",
            "/sdk/ohos/5.0.1.111/ets/component",
            "/sdk/ohos/5.0.1.111/ets/kits",
            "/sdk/typescript",
        ).map {
            val sdkPath = getResourcePath(it)
            loadEtsProjectAutoConvert(sdkPath, useArkAnalyzerTypeInference = null)
        }
        EtsScene(
            projectFiles = project.projectFiles,
            sdkFiles = sdks.flatMap { it.projectFiles },
            projectName = project.projectName,
        )
    }

    println("=== Import Resolver Testing ===")
    println("Scene loaded with ${scene.projectFiles.size} project files and ${scene.sdkFiles.size} SDK files")

    // Test import resolution for each file in the scene
    testImportResolverForAllFiles(scene)

    // Test specific import patterns
    // testImportPatterns(scene)
}

private fun testImportResolverForAllFiles(scene: EtsScene) {
    println("\n--- Testing import resolver for all files in scene ---")

    val allFiles = scene.projectFiles
    println("Total files to process: ${allFiles.size}")

    var totalImports = 0
    var successfulImports = 0
    var failedImports = 0
    var errorImports = 0

    allFiles.forEachIndexed { index, currentFile ->
        val fileName = currentFile.signature.fileName
        val imports = currentFile.importInfos

        if (imports.isEmpty()) {
            println("\n[${index + 1}/${allFiles.size}] File: $fileName (no imports)")
        } else {
            println("\n[${index + 1}/${allFiles.size}] File: $fileName (${imports.size} imports)")

            imports.forEach { importInfo ->
                totalImports++
                val importPath = importInfo.from
                val importName = importInfo.name
                val importType = importInfo.type

                when (val result = scene.resolveImport(currentFile, importPath)) {
                    is ImportResolutionResult.Success -> {
                        successfulImports++
                        println("  ✅ '$importName' from '$importPath' -> '${result.file.signature.fileName}' (type: $importType)")
                    }

                    is ImportResolutionResult.NotFound -> {
                        failedImports++
                        println("  ❌ '$importName' from '$importPath' -> ${result.reason} (type: $importType)")
                    }

                    is ImportResolutionResult.Error -> {
                        errorImports++
                        println("  ❗ '$importName' from '$importPath' -> Unexpected error: ${result.exception.message} (type: $importType)")
                    }
                }
            }
        }
    }

    // Print summary statistics
    println("\n--- Import Resolution Summary ---")
    println("Total imports found: $totalImports")
    println("Successfully resolved: $successfulImports")
    println("Failed to resolve: $failedImports")
    println("Unexpected errors: $errorImports")
    if (totalImports > 0) {
        val successRate = (successfulImports.toDouble() / totalImports * 100).toInt()
        println("Success rate: $successRate%")
    }
}

private fun testImportPatterns(scene: EtsScene) {
    println("\n--- Testing specific import patterns ---")

    if (scene.projectFiles.isEmpty()) {
        println("No project files available for testing")
        return
    }

    val testFile = scene.projectFiles.first()
    println("Using test file: ${testFile.signature.fileName}")

    // Test cases for different import types
    val testCases = mapOf(
        "System Library Imports" to listOf(
            "@ohos.router",
            "@ohos.app.ability.UIAbility",
            "@ohos.hilog",
            "@system.app",
            "@system.router"
        ),
        "Relative Imports" to listOf(
            "./component",
            "../utils/helper",
            "../../shared/types",
            "./index",
            "../common"
        ),
        "Absolute Imports" to listOf(
            "/src/main",
            "/utils/common",
            "/types/definitions",
            "/components/base"
        )
    )

    testCases.forEach { (category, imports) ->
        println("\n$category:")
        imports.forEach { importPath ->
            when (val result = scene.resolveImport(testFile, importPath)) {
                is ImportResolutionResult.Success -> {
                    println("  ✓ '$importPath' resolved to '${result.file.signature.fileName}'")
                }

                is ImportResolutionResult.NotFound -> {
                    println("  ✗ '$importPath' failed: ${result.reason}")
                }

                is ImportResolutionResult.Error -> {
                    println("  ! '$importPath' error: ${result.exception.javaClass.simpleName}: ${result.exception.message}")
                }
            }
        }
    }

    // Test path normalization
    println("\nPath Normalization Tests:")
    val normalizationTests = listOf(
        "./a/../b" to "b",
        "a/./b" to "a/b",
        "a/../b/./c" to "b/c",
        "./a/b/../c" to "a/c",
        "a/b/../../c" to "c"
    )

    normalizationTests.forEach { (input, expected) ->
        val result = normalizeRelativePath(input)
        val status = if (result == expected) "✓" else "✗"
        println("  $status '$input' -> '$result' (expected: '$expected')")
    }

    // Summary statistics
    println("\n--- Summary ---")
    println("Project files: ${scene.projectFiles.size}")
    println("SDK files: ${scene.sdkFiles.size}")
    println("Total files: ${scene.projectAndSdkClasses.size}")
    println("Total imports in project files: ${scene.projectFiles.sumOf { it.importInfos.size }}")

    val projectFilesByExtension = scene.projectFiles.groupBy {
        it.signature.fileName.substringAfterLast(".", "no-ext")
    }
    println("Project files by extension: ${projectFilesByExtension.mapValues { it.value.size }}")

    val sdkFilesByExtension = scene.sdkFiles.groupBy {
        it.signature.fileName.substringAfterLast(".", "no-ext")
    }
    println("SDK files by extension: ${sdkFilesByExtension.mapValues { it.value.size }}")
}

// FOR REFERENCE:
//
// class EtsScene(
//     val projectFiles: List<EtsFile>,
//     val sdkFiles: List<EtsFile> = emptyList(),
//     val projectName: String? = null,
// ) : CommonProject {
//     init {
//         projectFiles.forEach { it.scene = this }
//         sdkFiles.forEach { it.scene = this }
//     }
//
//     val projectClasses: List<EtsClass>
//         get() = projectFiles.flatMap { it.allClasses }
//
//     val sdkClasses: List<EtsClass>
//         get() = sdkFiles.flatMap { it.allClasses }
//
//     val projectAndSdkClasses: List<EtsClass>
//         get() = projectClasses + sdkClasses
// }
//
// class EtsFile(
//     val signature: EtsFileSignature,
//     val classes: List<EtsClass>,
//     val namespaces: List<EtsNamespace>,
//     val importInfos: List<EtsImportInfo> = emptyList(),
//     val exportInfos: List<EtsExportInfo> = emptyList(),
// )
//
// data class EtsFileSignature(
//     val projectName: String,
//     val fileName: String,
// )
//
// interface EtsClass : Base {
//     val signature: EtsClassSignature
//     val typeParameters: List<EtsType>
//     val fields: List<EtsField>
//     val methods: List<EtsMethod>
//     val ctor: EtsMethod
//     val category: EtsClassCategory
//     val superClass: EtsClassSignature?
//     val implementedInterfaces: List<EtsClassSignature>
//
//     val declaringFile: EtsFile?
//     val declaringNamespace: EtsNamespace?
//
//     val name: String
//         get() = signature.name
// }
//
// data class EtsClassSignature(
//     val name: String,
//     val file: EtsFileSignature,
//     val namespace: EtsNamespaceSignature? = null,
// )
//
// interface EtsMethod : Base, CommonMethod {
//     val signature: EtsMethodSignature
//     val typeParameters: List<EtsType>
//     val cfg: EtsBlockCfg
//
//     val enclosingClass: EtsClass?
//
//     override val name: String
//         get() = signature.name
//
//     override val parameters: List<EtsMethodParameter>
//         get() = signature.parameters
//
//     override val returnType: EtsType
//         get() = signature.returnType
//
//     override fun flowGraph(): EtsBytecodeGraph<EtsStmt> {
//         return cfg
//     }
// }
//
// data class EtsMethodSignature(
//     val enclosingClass: EtsClassSignature,
//     val name: String,
//     val parameters: List<EtsMethodParameter>,
//     val returnType: EtsType,
// )
//
// class EtsNamespace(
//     val signature: EtsNamespaceSignature,
//     val classes: List<EtsClass>,
//     val namespaces: List<EtsNamespace>,
// )
//
// data class EtsNamespaceSignature(
//     val name: String,
//     val file: EtsFileSignature,
//     val namespace: EtsNamespaceSignature? = null,
// )
//
// data class EtsImportInfo(
//     val name: String,
//     val type: EtsImportType,
//     val from: String,
//     val nameBeforeAs: String? = null,
//     override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
// ) : Base
//
// data class EtsExportInfo(
//     val name: String,
//     val type: EtsExportType,
//     val from: String? = null,
//     val nameBeforeAs: String? = null,
//     override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
// ) : Base
