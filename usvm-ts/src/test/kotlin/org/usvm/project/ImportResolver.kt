package org.usvm.project

import mu.KotlinLogging
import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.usvm.util.ImportResolutionResult
import org.usvm.util.SymbolResolutionResult
import org.usvm.util.abort
import org.usvm.util.getResourcePathOrNull
import org.usvm.util.normalizeRelativePath
import org.usvm.util.resolveImport
import org.usvm.util.resolveImportInfo
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/** Reports resolution outcomes for an optional demo project without assuming every external import is available. */
@Tag("manual")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Demo_Photos import resolution diagnostics")
class ImportResolverTest {

    companion object {
        private const val TEST_PROJECT_PATH = "/projects/Demo_Photos/source"
    }

    private lateinit var scene: EtsScene

    @BeforeAll
    fun setupScene() {
        logger.info { "\n--- Setting up scene for import resolution tests ---" }
        scene = run {
            val path = TEST_PROJECT_PATH
            logger.info { "Loading project from resources: $path" }
            val projectPath = getResourcePathOrNull(path)
            if (projectPath == null) {
                logger.warn { "Project '$path' not found in resources. Ensure the project is available." }
                abort()
            }

            logger.info { "Loading project from path: $projectPath" }
            val projectScene = loadEtsProjectAutoConvert(projectPath)
            logger.info { "Project loaded: ${projectScene.projectName} with ${projectScene.projectFiles.size} files" }
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
                logger.info { "Loading SDK from resource: $it" }
                val sdkPath = getResourcePathOrNull(it)
                assertNotNull(
                    sdkPath,
                    "SDK path '$it' not found in resources. Ensure the SDK is available."
                )

                logger.info { "Loading SDK from path: $sdkPath" }
                val sdkScene = loadEtsProjectAutoConvert(sdkPath, useArkAnalyzerTypeInference = null)
                logger.info { "SDK loaded: ${sdkScene.projectName} with ${sdkScene.projectFiles.size} files" }
                assertTrue(
                    sdkScene.projectFiles.isNotEmpty(),
                    "No SDK files found in the SDK at '$it'. Ensure the SDK is correctly set up."
                )

                sdkScene
            }

            logger.info { "Merging project and SDK files..." }
            EtsScene(
                projectFiles = projectScene.projectFiles,
                sdkFiles = sdks.flatMap { it.projectFiles },
                projectName = projectScene.projectName,
            )
        }

        logger.info {
            "Scene loaded with ${scene.projectFiles.size} project files and ${scene.sdkFiles.size} SDK files"
        }
    }

    @Test
    @DisplayName("Report file-level import resolution across project files")
    fun reportFileImportResolution() {
        logger.info { "\n--- Reporting file-level import resolution ---" }

        val allFiles = scene.projectFiles
        var totalImports = 0
        var successfulImports = 0
        var failedImports = 0

        allFiles.forEachIndexed { index, currentFile ->
            val fileName = currentFile.signature.fileName
            val imports = currentFile.importInfos

            if (imports.isEmpty()) {
                logger.info { "\n[${index + 1}/${allFiles.size}] File: $fileName (no imports)" }
            } else {
                logger.info { "\n[${index + 1}/${allFiles.size}] File: $fileName (${imports.size} imports)" }

                imports.forEach { importInfo ->
                    totalImports++

                    when (val result = scene.resolveImport(currentFile, importInfo.from)) {
                        is ImportResolutionResult.Success -> {
                            successfulImports++
                            logger.info {
                                "  ✅ '${importInfo.name}' from '${importInfo.from}' -> '${result.file.signature.fileName}'" +
                                    " (type: ${importInfo.type})"
                            }
                        }

                        is ImportResolutionResult.NotFound -> {
                            failedImports++
                            logger.info {
                                "  ❌ '${importInfo.name}' from '${importInfo.from}' -> ${result.reason}" +
                                    " (type: ${importInfo.type})"
                            }
                        }
                    }
                }
            }
        }

        logger.info { "\n--- File Import Resolution Summary ---" }
        logger.info { "Total imports found: $totalImports" }
        logger.info { "Successfully resolved files: $successfulImports" }
        logger.info { "Failed to resolve files: $failedImports" }
        if (totalImports > 0) {
            val successRate = (successfulImports.toDouble() / totalImports * 100).toInt()
            logger.info { "File resolution success rate: $successRate%" }
        }

        assertTrue(totalImports > 0, "Should find at least some imports in the project files")
    }

    @Test
    @DisplayName("Report symbol resolution across project imports")
    fun reportSymbolResolution() {
        logger.info { "\n--- Reporting symbol resolution ---" }

        val allFiles = scene.projectFiles
        var totalSymbols = 0
        var successfulSymbols = 0
        var fileNotFoundSymbols = 0
        var symbolNotFoundSymbols = 0

        allFiles.forEachIndexed { index, currentFile ->
            val fileName = currentFile.signature.fileName
            val imports = currentFile.importInfos

            if (imports.isEmpty()) {
                logger.info { "\n[${index + 1}/${allFiles.size}] File: '$fileName' (no imports)" }
            } else {
                logger.info { "\n[${index + 1}/${allFiles.size}] File: '$fileName' (${imports.size} imports)" }

                imports.forEach { importInfo ->
                    totalSymbols++

                    val result = scene.resolveImportInfo(currentFile, importInfo)

                    when (result) {
                        is SymbolResolutionResult.Success -> {
                            successfulSymbols++
                            val exportInfo = result.exportInfo
                            logger.info {
                                "  🎯 '${importInfo.name}' from '${importInfo.from}' -> '${result.file.signature.fileName}'" +
                                    " exports: ${getExportDescription(exportInfo)} (import type: ${importInfo.type})"
                            }
                        }

                        is SymbolResolutionResult.FileNotFound -> {
                            fileNotFoundSymbols++
                            logger.info {
                                "  📁❌ '${importInfo.name}' from '${importInfo.from}' -> ${result.reason}" +
                                    " (import type: ${importInfo.type})"
                            }
                        }

                        is SymbolResolutionResult.SymbolNotFound -> {
                            symbolNotFoundSymbols++
                            logger.info {
                                "  🔍❌ '${importInfo.name}' from '${importInfo.from}' -> ${result.reason}" +
                                    " (import type: ${importInfo.type})"
                            }
                            val availableExports = result.file.exportInfos
                            if (availableExports.isNotEmpty()) {
                                val exportNames = availableExports.map { it.name }.take(5)
                                logger.info {
                                    "      Available exports: ${
                                        exportNames.joinToString(", ")
                                    }${
                                        if (availableExports.size > 5) " ..." else ""
                                    }"
                                }
                            }
                        }
                    }
                }
            }
        }

        logger.info { "\n--- Complete Symbol Resolution Summary ---" }
        logger.info { "Total symbols to resolve: $totalSymbols" }
        logger.info { "Successfully resolved symbols: $successfulSymbols" }
        logger.info { "File not found: $fileNotFoundSymbols" }
        logger.info { "Symbol not found in file: $symbolNotFoundSymbols" }
        if (totalSymbols > 0) {
            val symbolSuccessRate = (successfulSymbols.toDouble() / totalSymbols * 100).toInt()
            logger.info { "Complete symbol resolution success rate: $symbolSuccessRate%" }
        }

        assertTrue(totalSymbols > 0, "Should find at least some symbols to resolve in the project files")
    }

    @Test
    @DisplayName("Report common import probes and check path normalization")
    fun reportCommonImportPatterns() {
        logger.info { "\n--- Reporting common import probes ---" }

        Assumptions.assumeTrue(
            scene.projectFiles.isNotEmpty(),
            "No project files found in the scene"
        )

        val testFile = scene.projectFiles.first()
        logger.info { "Using test file: ${testFile.signature.fileName}" }

        // Probe paths may be absent from the optional demo project.
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
            logger.info { "\n$category:" }
            imports.forEach { importPath ->
                totalTestedImports++
                when (val result = scene.resolveImport(testFile, importPath)) {
                    is ImportResolutionResult.Success -> {
                        successfulImports++
                        logger.info { "  ✓ '$importPath' resolved to '${result.file.signature.fileName}'" }
                    }

                    is ImportResolutionResult.NotFound -> {
                        logger.info { "  ✗ '$importPath' failed: ${result.reason}" }
                    }
                }
            }
        }

        // Test path normalization
        logger.info { "\nPath Normalization Tests:" }
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
            logger.info { "  $status '$input' -> '$result' (expected: '$expected')" }
        }

        // Summary statistics
        logger.info { "\n--- Summary ---" }
        logger.info { "Project files: ${scene.projectFiles.size}" }
        logger.info { "SDK files: ${scene.sdkFiles.size}" }
        logger.info { "Total files: ${scene.projectAndSdkClasses.size}" }
        logger.info { "Total imports in project files: ${scene.projectFiles.sumOf { it.importInfos.size }}" }

        val projectFilesByExtension = scene.projectFiles.groupBy {
            it.signature.fileName.substringAfterLast(".", "no-ext")
        }
        logger.info { "Project files by extension: ${projectFilesByExtension.mapValues { it.value.size }}" }

        val sdkFilesByExtension = scene.sdkFiles.groupBy {
            it.signature.fileName.substringAfterLast(".", "no-ext")
        }
        logger.info { "SDK files by extension: ${sdkFilesByExtension.mapValues { it.value.size }}" }

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
            EtsExportType.NAMESPACE -> "namespace ${exportInfo.name}"
            EtsExportType.CLASS -> "class ${exportInfo.name}"
            EtsExportType.METHOD -> "method ${exportInfo.name}"
            EtsExportType.LOCAL -> "local ${exportInfo.name}"
            EtsExportType.TYPE -> "type ${exportInfo.name}"
            EtsExportType.UNKNOWN -> "unknown export ${exportInfo.name}"
        }
    }
}
