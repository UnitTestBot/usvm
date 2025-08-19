package org.usvm.util

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert

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

    // Test common import patterns
    testCommonImportPatterns(scene)

    // Test import resolution for each file in the scene
    testImportResolverForAllFiles(scene)
}

private fun testImportResolverForAllFiles(scene: EtsScene) {
    println("\n--- Testing import resolver for all files in scene ---")

    val allFiles = scene.projectFiles
    println("Total files to process: ${allFiles.size}")

    var totalImports = 0
    var successfulImports = 0
    var failedImports = 0

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
                }
            }
        }
    }

    // Print summary statistics
    println("\n--- Import Resolution Summary ---")
    println("Total imports found: $totalImports")
    println("Successfully resolved: $successfulImports")
    println("Failed to resolve: $failedImports")
    if (totalImports > 0) {
        val successRate = (successfulImports.toDouble() / totalImports * 100).toInt()
        println("Success rate: $successRate%")
    }
}

private fun testCommonImportPatterns(scene: EtsScene) {
    println("\n--- Testing common import patterns ---")

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
            "@system.router",
        ),
        "Relative Imports" to listOf(
            "./component",
            "../utils/helper",
            "../../shared/types",
            "./index",
            "../common",
        ),
        "Absolute Imports" to listOf(
            "/src/main",
            "/common/GlobalContext",
            "/utils/Log",
            "/models/Action",
            "/components/ToolBar",
            "/pages/Index",
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
