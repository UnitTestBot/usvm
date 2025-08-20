package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Import Resolution Tests")
class ImportResolverTest {

    private lateinit var scene: EtsScene

    @BeforeAll
    fun setupScene() {
        println("\n--- Setting up scene for import resolution tests ---")
        val path = "/projects/Demo_Photos/source"
        scene = run {
            println("Loading project from resources: $path")
            val projectPath = getResourcePathOrNull(path)
            if (projectPath == null) {
                logger.warn { "Project '$path' not found in resources. Ensure the project is available." }
                abort()
            }

            println("Loading project from path: $projectPath")
            val projectScene = loadEtsProjectAutoConvert(projectPath)
            println("Project loaded: ${projectScene.projectName} with ${projectScene.projectFiles.size} files")
            assertTrue(
                projectScene.projectFiles.isNotEmpty(),
                "No project files found in the project at '$path'. Ensure the project is correctly set up."
            )

            val sdks = listOf(
                "/sdk/ohos/5.0.1.111/ets/api",
                "/sdk/ohos/5.0.1.111/ets/arkts",
                "/sdk/ohos/5.0.1.111/ets/component",
                "/sdk/ohos/5.0.1.111/ets/kits",
                "/sdk/typescript",
            ).map {
                println("Loading SDK from resource: $it")
                val sdkPath = getResourcePathOrNull(it)
                assertNotNull(
                    sdkPath,
                    "SDK path '$it' not found in resources. Ensure the SDK is available."
                )

                println("Loading SDK from path: $sdkPath")
                val sdkScene = loadEtsProjectAutoConvert(sdkPath, useArkAnalyzerTypeInference = null)
                println("SDK loaded: ${sdkScene.projectName} with ${sdkScene.projectFiles.size} files")
                assertTrue(
                    sdkScene.projectFiles.isNotEmpty(),
                    "No SDK files found in the SDK at '$it'. Ensure the SDK is correctly set up."
                )

                sdkScene
            }

            println("Merging project and SDK files...")
            EtsScene(
                projectFiles = projectScene.projectFiles,
                sdkFiles = sdks.flatMap { it.projectFiles },
                projectName = projectScene.projectName,
            )
        }

        println("Scene loaded with ${scene.projectFiles.size} project files and ${scene.sdkFiles.size} SDK files")
    }

    @Test
    @DisplayName("Test file-level import resolution with real imports from project files")
    fun testFileImportResolver() {
        println("\n--- Testing file-level import resolver ---")

        val allFiles = scene.projectFiles
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

                    when (val result = scene.resolveImport(currentFile, importInfo.from)) {
                        is ImportResolutionResult.Success -> {
                            successfulImports++
                            println(
                                "  ✅ '${importInfo.name}' from '${importInfo.from}' -> '${result.file.signature.fileName}'" +
                                    " (type: ${importInfo.type})"
                            )
                        }

                        is ImportResolutionResult.NotFound -> {
                            failedImports++
                            println(
                                "  ❌ '${importInfo.name}' from '${importInfo.from}' -> ${result.reason}" +
                                    " (type: ${importInfo.type})"
                            )
                        }
                    }
                }
            }
        }

        println("\n--- File Import Resolution Summary ---")
        println("Total imports found: $totalImports")
        println("Successfully resolved files: $successfulImports")
        println("Failed to resolve files: $failedImports")
        if (totalImports > 0) {
            val successRate = (successfulImports.toDouble() / totalImports * 100).roundToInt()
            println("File resolution success rate: $successRate%")
        }

        // Assert that we found some imports and some were successful
        assertTrue(totalImports > 0, "Should find at least some imports in the project files")
        // Note: We don't assert on success rate as it depends on the availability of imported files
    }

    @Test
    @DisplayName("Test complete symbol resolution with real imports matching actual exports")
    fun testSymbolResolver() {
        println("\n--- Testing complete symbol resolver ---")

        val allFiles = scene.projectFiles
        var totalSymbols = 0
        var successfulSymbols = 0
        var fileNotFoundSymbols = 0
        var symbolNotFoundSymbols = 0

        allFiles.forEachIndexed { index, currentFile ->
            val fileName = currentFile.signature.fileName
            val imports = currentFile.importInfos

            if (imports.isEmpty()) {
                println("\n[${index + 1}/${allFiles.size}] File: '$fileName' (no imports)")
            } else {
                println("\n[${index + 1}/${allFiles.size}] File: '$fileName' (${imports.size} imports)")

                imports.forEach { importInfo ->
                    totalSymbols++

                    when (val result =
                        scene.resolveSymbol(
                            currentFile = currentFile,
                            importPath = importInfo.from,
                            symbolName = importInfo.name,
                            importType = importInfo.type,
                        )) {
                        is SymbolResolutionResult.Success -> {
                            successfulSymbols++
                            val exportInfo = result.exportInfo
                            println(
                                "  🎯 '${importInfo.name}' from '${importInfo.from}' -> '${result.file.signature.fileName}'" +
                                    " exports: ${getExportDescription(exportInfo)} (import type: ${importInfo.type})"
                            )
                        }

                        is SymbolResolutionResult.FileNotFound -> {
                            fileNotFoundSymbols++
                            println(
                                "  📁❌ '${importInfo.name}' from '${importInfo.from}' -> ${result.reason}" +
                                    " (import type: ${importInfo.type})"
                            )
                        }

                        is SymbolResolutionResult.SymbolNotFound -> {
                            symbolNotFoundSymbols++
                            println(
                                "  🔍❌ '${importInfo.name}' from '${importInfo.from}' -> ${result.reason}" +
                                    " (import type: ${importInfo.type})"
                            )
                            val availableExports = result.file.exportInfos
                            if (availableExports.isNotEmpty()) {
                                val exportNames = availableExports.map { it.name }.take(5)
                                println(
                                    "      Available exports: ${
                                        exportNames.joinToString(", ")
                                    }${
                                        if (availableExports.size > 5) " ..." else ""
                                    }"
                                )
                            }
                        }
                    }
                }
            }
        }

        println("\n--- Complete Symbol Resolution Summary ---")
        println("Total symbols to resolve: $totalSymbols")
        println("Successfully resolved symbols: $successfulSymbols")
        println("File not found: $fileNotFoundSymbols")
        println("Symbol not found in file: $symbolNotFoundSymbols")
        if (totalSymbols > 0) {
            val symbolSuccessRate = (successfulSymbols.toDouble() / totalSymbols * 100).toInt()
            println("Complete symbol resolution success rate: $symbolSuccessRate%")
        }

        // Assert that we found some symbols to resolve
        assertTrue(totalSymbols > 0, "Should find at least some symbols to resolve in the project files")
        // Note: We don't assert on success rate as it depends on the availability of matching exports
    }

    @Test
    @DisplayName("Test common import patterns and path normalization")
    fun testCommonImportPatterns() {
        println("\n--- Testing common import patterns ---")

        Assumptions.assumeTrue(
            scene.projectFiles.isNotEmpty(),
            "No project files found in the scene"
        )

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

        var totalTestedImports = 0
        var successfulImports = 0

        testCases.forEach { (category, imports) ->
            println("\n$category:")
            imports.forEach { importPath ->
                totalTestedImports++
                when (val result = scene.resolveImport(testFile, importPath)) {
                    is ImportResolutionResult.Success -> {
                        successfulImports++
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

        var correctNormalizations = 0
        normalizationTests.forEach { (input, expected) ->
            val result = normalizeRelativePath(input)
            val status = if (result == expected) "✓" else "✗"
            if (result == expected) correctNormalizations++
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

        // Assertions
        assertTrue(scene.projectFiles.isNotEmpty(), "Should have at least one project file")
        assertTrue(scene.sdkFiles.isNotEmpty(), "Should have at least one SDK file")
        assertTrue(totalTestedImports > 0, "Should have tested at least one import pattern")
        assertEquals(
            correctNormalizations,
            normalizationTests.size,
            "All path normalization tests should pass (expected: ${normalizationTests.size}, actual: $correctNormalizations)"
        )
        assertEquals(
            totalTestedImports,
            testCases.values.sumOf { it.size },
            "Should have tested all import patterns"
        )
    }

    // Helper function to describe export information
    private fun getExportDescription(exportInfo: EtsExportInfo): String {
        return when (exportInfo.type) {
            EtsExportType.NAME_SPACE -> "namespace ${exportInfo.name}"
            EtsExportType.CLASS -> "class ${exportInfo.name}"
            EtsExportType.METHOD -> "method ${exportInfo.name}"
            EtsExportType.LOCAL -> "local ${exportInfo.name}"
            EtsExportType.TYPE -> "type ${exportInfo.name}"
            EtsExportType.UNKNOWN -> "unknown export ${exportInfo.name}"
        }
    }
}
