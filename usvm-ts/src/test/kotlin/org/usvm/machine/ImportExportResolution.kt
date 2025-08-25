package org.usvm.machine

import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsImportInfo
import org.jacodb.ets.model.EtsImportType
import org.jacodb.ets.model.EtsModifier
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.usvm.util.ImportResolutionResult
import org.usvm.util.SymbolResolutionResult
import org.usvm.util.resolveImport
import org.usvm.util.resolveImportInfo
import org.usvm.util.resolveSymbol
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("Import and Export Resolution Tests")
class ImportExportResolutionTest {

    private lateinit var scene: EtsScene
    private lateinit var currentFile: EtsFile
    private lateinit var targetFile1: EtsFile
    private lateinit var targetFile2: EtsFile
    private lateinit var systemLibraryFile: EtsFile

    @BeforeEach
    fun setup() {
        // Create target file with various export types
        targetFile1 = createMockFile(
            signature = EtsFileSignature("TestProject", "src/utils/helper.ets"),
            exports = listOf(
                // Default export:
                //  export default class Helper {}
                EtsExportInfo(
                    "default",
                    EtsExportType.CLASS,
                    nameBeforeAs = "Helper",
                    modifiers = EtsModifiers.of(EtsModifier.DEFAULT)
                ),

                // Named exports:
                //  export function utility() {}
                EtsExportInfo("utility", EtsExportType.METHOD),
                //  export const CONSTANT = 42;
                EtsExportInfo("CONSTANT", EtsExportType.LOCAL),

                // Aliased export:
                //  function internalName() {}
                //  export { internalName as PublicName };
                EtsExportInfo("PublicName", EtsExportType.METHOD, nameBeforeAs = "internalName"),

                // Re-export from another module:
                //  export class ExternalUtil {}  (in 'src/utils/external.ts')
                //  export { ExternalUtil } from './external';
                EtsExportInfo("ExternalUtil", EtsExportType.CLASS, from = "./external"),
            )
        )

        // Create another target file with namespace and type exports
        targetFile2 = createMockFile(
            signature = EtsFileSignature("TestProject", "src/types/index.ets"),
            exports = listOf(
                // Namespace export:
                //  export namespace Types {}
                EtsExportInfo("Types", EtsExportType.NAME_SPACE),

                // Type export:
                //  export type UserType = { name: string; }
                EtsExportInfo("UserType", EtsExportType.TYPE),

                // Star re-export:
                //  export * from './all-types';
                EtsExportInfo("*", EtsExportType.NAME_SPACE, from = "./all-types"),
            )
        )

        // Create system library file
        systemLibraryFile = createMockFile(
            signature = EtsFileSignature("SystemLibrary", "@ohos.router.d.ts"),
            exports = listOf(
                EtsExportInfo("Router", EtsExportType.CLASS),
                EtsExportInfo("navigate", EtsExportType.METHOD),
            )
        )

        // Create current file
        currentFile = createMockFile(
            signature = EtsFileSignature("TestProject", "src/components/Component.ets"),
            exports = emptyList()
        )

        // Create scene with all files
        scene = EtsScene(
            projectFiles = listOf(currentFile, targetFile1, targetFile2),
            sdkFiles = listOf(systemLibraryFile),
            projectName = "TestProject",
        )
    }

    // Test file resolution for different path types
    @Test
    @DisplayName("Test system library import resolution")
    fun testSystemLibraryResolution() {
        val result = scene.resolveImport(currentFile, "@ohos.router")
        assertIs<ImportResolutionResult.Success>(result)
        assertEquals("@ohos.router.d.ts", result.file.signature.fileName)
    }

    @Test
    @DisplayName("Test relative import resolution")
    fun testRelativeImportResolution() {
        val result = scene.resolveImport(currentFile, "../utils/helper")
        assertIs<ImportResolutionResult.Success>(result)
        assertEquals("src/utils/helper.ets", result.file.signature.fileName)
    }

    @Test
    @DisplayName("Test absolute import resolution")
    fun testAbsoluteImportResolution() {
        val result = scene.resolveImport(currentFile, "/src/types/index")
        assertIs<ImportResolutionResult.Success>(result)
        assertEquals("src/types/index.ets", result.file.signature.fileName)
    }

    @Test
    @DisplayName("Test import resolution failure")
    fun testImportNotFound() {
        val result = scene.resolveImport(currentFile, "./nonexistent")
        assertIs<ImportResolutionResult.NotFound>(result)
        assertTrue(result.reason.contains("File not found"))
    }

    // Test symbol resolution for default imports
    @Test
    @DisplayName("Test default import symbol resolution")
    fun testDefaultImportResolution() {
        // Import a default symbol:
        //  import Helper from '../utils/helper';
        //  export default Helper;  (in helper.ets)
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "Helper",
            importType = EtsImportType.DEFAULT,
        )
        assertIs<SymbolResolutionResult.Success>(result)
        assertTrue(result.exportInfo.isDefaultExport)
        assertEquals("default", result.exportInfo.name)
        assertEquals("Helper", result.exportInfo.originalName)
        assertEquals(EtsExportType.CLASS, result.exportInfo.type)
    }

    @Test
    @DisplayName("Test default import when no default export exists")
    fun testDefaultImportNoDefaultExport() {
        // Try to import a default export that doesn't exist:
        //  import Types from '/src/types/index';
        // Should fail since index.ets does not have a default export
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "/src/types/index",
            symbolName = "Types",
            importType = EtsImportType.DEFAULT,
        )
        assertIs<SymbolResolutionResult.SymbolNotFound>(result)
        assertTrue(result.reason.contains("Default export not found"))
    }

    // Test symbol resolution for named imports
    @Test
    @DisplayName("Test named import symbol resolution")
    fun testNamedImportResolution() {
        // Import a named symbol:
        //  import { utility } from '../utils/helper';
        //  export function utility() {}  (in helper.ets)
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "utility",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("utility", result.exportInfo.name)
        assertEquals(EtsExportType.METHOD, result.exportInfo.type)
    }

    @Test
    @DisplayName("Test named import with aliased export")
    fun testNamedImportWithAlias() {
        // Import an aliased symbol:
        //  import { PublicName } from '../utils/helper';
        //  export { internalName as PublicName };  (in helper.ets)
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "PublicName",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("PublicName", result.exportInfo.name)
        assertEquals("internalName", result.exportInfo.originalName)
        assertTrue(result.exportInfo.isAliased)
    }

    @Test
    @DisplayName("Test named import matching original name before alias")
    fun testNamedImportMatchingOriginalName() {
        // Try to import the aliased symbol by its original name:
        //  import { internalName } from '../utils/helper';
        //  export { internalName as PublicName };
        // Should fail since 'internalName' is not exported directly, but aliased to 'PublicName'.
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "internalName",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.SymbolNotFound>(result)
        assertTrue(result.reason.contains("Named export 'internalName' not found"))
    }

    @Test
    @DisplayName("Test named import when symbol not found")
    fun testNamedImportNotFound() {
        // Try to import a non-existent symbol:
        //  import { nonexistent } from '../utils/helper';
        // no such export in helper.ets
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "nonexistent",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.SymbolNotFound>(result)
        assertTrue(result.reason.contains("Named export 'nonexistent' not found"))
    }

    // Test symbol resolution for namespace imports
    @Test
    @DisplayName("Test namespace import symbol resolution")
    fun testNamespaceImportResolution() {
        // Import all symbols from a module:
        //  import * as HelperModule from '../utils/helper';
        // Should resolve to a virtual namespace export
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "HelperModule",
            importType = EtsImportType.NAMESPACE,
        )
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("HelperModule", result.exportInfo.name)
        assertEquals(EtsExportType.NAME_SPACE, result.exportInfo.type)
    }

    @Test
    @DisplayName("Test namespace import when no exports exist")
    fun testNamespaceImportNoExports() {
        // Create file with no exports
        val emptyFileSignature = EtsFileSignature("TestProject", "src/empty.ets")
        val emptyFile = createMockFile(emptyFileSignature, exports = emptyList())
        val sceneWithEmpty = EtsScene(
            projectFiles = listOf(currentFile, emptyFile),
            sdkFiles = emptyList(),
            projectName = "TestProject",
        )

        // Import all symbols from an empty module:
        //  import * as EmptyModule from '/src/empty';
        // Should fail since there are no exports in empty.ets
        val result = sceneWithEmpty.resolveSymbol(
            currentFile = currentFile,
            importPath = "/src/empty",
            symbolName = "EmptyModule",
            importType = EtsImportType.NAMESPACE,
        )
        assertIs<SymbolResolutionResult.SymbolNotFound>(result)
        assertTrue(result.reason.contains("No exports found for namespace import"))
    }

    // Test symbol resolution for side effect imports
    @Test
    @DisplayName("Test side effect import symbol resolution")
    fun testSideEffectImportResolution() {
        // Import a module for its side effects:
        //  import '../utils/helper';
        // Should resolve to a virtual export with no specific symbol
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "",
            importType = EtsImportType.SIDE_EFFECT,
        )
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("", result.exportInfo.name)
        assertEquals(EtsExportType.UNKNOWN, result.exportInfo.type)
    }

    // Test complete import info resolution
    @Test
    @DisplayName("Test complete import info resolution for default import")
    fun testCompleteDefaultImportInfo() {
        // Import a default symbol:
        //  import Helper from '../utils/helper';
        val importInfo = EtsImportInfo(
            name = "Helper",
            type = EtsImportType.DEFAULT,
            from = "../utils/helper",
        )

        // export default class Helper {}  (in helper.ets)
        val result = scene.resolveImportInfo(currentFile, importInfo)
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("default", result.exportInfo.name)
        assertEquals("Helper", result.exportInfo.originalName)
    }

    @Test
    @DisplayName("Test complete import info resolution for named import")
    fun testCompleteNamedImportInfo() {
        // Import a named symbol:
        //  import { utility } from '../utils/helper';
        val importInfo = EtsImportInfo(
            name = "utility",
            type = EtsImportType.NAMED,
            from = "../utils/helper",
        )

        // export function utility() {}  (in helper.ets)
        val result = scene.resolveImportInfo(currentFile, importInfo)
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("utility", result.exportInfo.name)
        assertEquals(EtsExportType.METHOD, result.exportInfo.type)
    }

    @Test
    @DisplayName("Test complete import info resolution for namespace import")
    fun testCompleteNamespaceImportInfo() {
        // Import all symbols from a module:
        //  import * as HelperNamespace from '../utils/helper';
        val importInfo = EtsImportInfo(
            name = "HelperNamespace",
            type = EtsImportType.NAMESPACE,
            from = "../utils/helper",
        )

        // Should resolve to a virtual namespace export
        val result = scene.resolveImportInfo(currentFile, importInfo)
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("HelperNamespace", result.exportInfo.name)
        assertEquals(EtsExportType.NAME_SPACE, result.exportInfo.type)
    }

    // Test various export scenarios
    @Test
    @DisplayName("Test resolution with re-exported symbols")
    fun testReExportedSymbolResolution() {
        // Import re-exported symbol:
        //  import { ExternalUtil } from '../utils/helper';
        //  export { ExternalUtil } from './external';  (in helper.ets)
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "../utils/helper",
            symbolName = "ExternalUtil",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.Success>(result)
        assertEquals("ExternalUtil", result.exportInfo.name)
        assertEquals("./external", result.exportInfo.from)
    }

    @Test
    @DisplayName("Test path normalization in relative imports")
    fun testPathNormalizationInImports() {
        // Test complex relative path that should normalize to the target
        val result = scene.resolveImport(currentFile, "./../../src/utils/helper")
        assertIs<ImportResolutionResult.Success>(result)
        assertEquals("src/utils/helper.ets", result.file.signature.fileName)
    }

    @Test
    @DisplayName("Test file resolution with different extensions")
    fun testFileResolutionWithExtensions() {
        // Should match files with .ets extension even when not specified
        val result1 = scene.resolveImport(currentFile, "../utils/helper")
        assertIs<ImportResolutionResult.Success>(result1)

        // Should also work with explicit .ets extension
        val result2 = scene.resolveImport(currentFile, "../utils/helper.ets")
        assertIs<ImportResolutionResult.Success>(result2)
    }

    // Test edge cases
    @Test
    @DisplayName("Test symbol resolution when file not found")
    fun testSymbolResolutionFileNotFound() {
        // Attempt to resolve a symbol from a non-existent file:
        // import { SomeSymbol } from './nonexistent';
        val result = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = "./nonexistent",
            symbolName = "SomeSymbol",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.FileNotFound>(result)
        assertTrue(result.reason.contains("File not found"))
    }

    @Test
    @DisplayName("Test various import types against same file")
    fun testMultipleImportTypesAgainstSameFile() {
        val filePath = "../utils/helper"

        // Test default import:
        //  import Helper from '../utils/helper';
        val defaultResult = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = filePath,
            symbolName = "Helper",
            importType = EtsImportType.DEFAULT,
        )
        assertIs<SymbolResolutionResult.Success>(defaultResult)

        // Test named import:
        //  import { utility } from '../utils/helper';
        val namedResult = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = filePath,
            symbolName = "utility",
            importType = EtsImportType.NAMED,
        )
        assertIs<SymbolResolutionResult.Success>(namedResult)

        // Test namespace import:
        //  import * as HelperNs from '../utils/helper';
        val namespaceResult = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = filePath,
            symbolName = "HelperNs",
            importType = EtsImportType.NAMESPACE,
        )
        assertIs<SymbolResolutionResult.Success>(namespaceResult)

        // Test side effect import
        //  import '../utils/helper';
        val sideEffectResult = scene.resolveSymbol(
            currentFile = currentFile,
            importPath = filePath,
            symbolName = "",
            importType = EtsImportType.SIDE_EFFECT,
        )
        assertIs<SymbolResolutionResult.Success>(sideEffectResult)
    }

    // Helper function to create mock EtsFile instances
    private fun createMockFile(signature: EtsFileSignature, exports: List<EtsExportInfo>): EtsFile {
        // Since EtsFile is a final class, we need to create it using its constructor
        return EtsFile(
            signature = signature,
            classes = emptyList(),
            namespaces = emptyList(),
            importInfos = emptyList(),
            exportInfos = exports,
        )
    }
}
