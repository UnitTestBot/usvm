package org.usvm.util

import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsImportInfo
import org.jacodb.ets.model.EtsImportType
import org.jacodb.ets.model.EtsScene

sealed class ImportResolutionResult {
    data class Success(val file: EtsFile) : ImportResolutionResult()
    data class NotFound(val reason: String) : ImportResolutionResult()
}

sealed class SymbolResolutionResult {
    data class Success(val file: EtsFile, val exportInfo: EtsExportInfo) : SymbolResolutionResult()
    data class FileNotFound(val reason: String) : SymbolResolutionResult()
    data class SymbolNotFound(val file: EtsFile, val symbolName: String, val reason: String) : SymbolResolutionResult()
}

fun EtsScene.resolveImport(
    currentFile: EtsFile,
    importPath: String,
): ImportResolutionResult {
    return try {
        when {
            importPath.startsWith("@") -> resolveSystemLibrary(importPath)
            importPath.startsWith("/") -> resolveAbsolutePath(importPath)
            else -> resolveRelativePath(currentFile, importPath)
        }
    } catch (e: Exception) {
        ImportResolutionResult.NotFound(e.message ?: "Unknown error")
    }
}

fun EtsScene.resolveSymbol(
    currentFile: EtsFile,
    importPath: String,
    symbolName: String,
    importType: EtsImportType,
): SymbolResolutionResult {
    return when (val fileResult = resolveImport(currentFile, importPath)) {
        is ImportResolutionResult.NotFound -> SymbolResolutionResult.FileNotFound(fileResult.reason)
        is ImportResolutionResult.Success -> resolveSymbolInFile(fileResult.file, symbolName, importType)
    }
}

fun EtsScene.resolveImportInfo(
    currentFile: EtsFile,
    importInfo: EtsImportInfo,
): SymbolResolutionResult {
    return resolveSymbol(currentFile, importInfo.from, importInfo.name, importInfo.type)
}

private fun EtsScene.resolveSystemLibrary(importPath: String): ImportResolutionResult {
    val foundFile = sdkFiles.find { file ->
        file.signature.fileName.equals(importPath, ignoreCase = true) ||
            file.signature.fileName.equals("$importPath.ts", ignoreCase = true) ||
            file.signature.fileName.equals("$importPath.ets", ignoreCase = true) ||
            file.signature.fileName.equals("$importPath.d.ts", ignoreCase = true)
    }

    return if (foundFile != null) {
        ImportResolutionResult.Success(foundFile)
    } else {
        ImportResolutionResult.NotFound("System library not found: '$importPath'")
    }
}

private fun EtsScene.resolveAbsolutePath(importPath: String): ImportResolutionResult {
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
        ImportResolutionResult.NotFound("File not found for absolute path: '$importPath'")
    }
}

private fun EtsScene.resolveRelativePath(currentFile: EtsFile, importPath: String): ImportResolutionResult {
    val currentFileName = currentFile.signature.fileName
    val currentDir = if (currentFileName.contains("/")) {
        currentFileName.substringBeforeLast("/")
    } else {
        ""
    }

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
        ImportResolutionResult.NotFound("File not found for relative path: '$importPath' from ${currentFile.signature.fileName}")
    }
}

private fun resolveSymbolInFile(
    targetFile: EtsFile,
    symbolName: String,
    importType: EtsImportType,
): SymbolResolutionResult {
    val exports = targetFile.exportInfos

    return when (importType) {
        EtsImportType.DEFAULT -> {
            val defaultExport = exports.find { it.isDefaultExport }
            if (defaultExport != null) {
                SymbolResolutionResult.Success(targetFile, defaultExport)
            } else {
                SymbolResolutionResult.SymbolNotFound(
                    targetFile,
                    symbolName,
                    "Default export not found in ${targetFile.signature.fileName}"
                )
            }
        }

        EtsImportType.NAMED -> {
            val namedExport = exports.find {
                it.name == symbolName || it.originalName == symbolName
            }
            if (namedExport != null) {
                SymbolResolutionResult.Success(targetFile, namedExport)
            } else {
                SymbolResolutionResult.SymbolNotFound(
                    targetFile,
                    symbolName,
                    "Named export '$symbolName' not found in ${targetFile.signature.fileName}"
                )
            }
        }

        EtsImportType.NAMESPACE -> {
            // For namespace imports, we create a virtual export that represents all exports
            if (exports.isNotEmpty()) {
                // Create a synthetic namespace export
                val namespaceExport = EtsExportInfo(
                    name = symbolName,
                    type = EtsExportType.NAME_SPACE,
                    from = null,
                    nameBeforeAs = null,
                )
                SymbolResolutionResult.Success(targetFile, namespaceExport)
            } else {
                SymbolResolutionResult.SymbolNotFound(
                    targetFile,
                    symbolName,
                    "No exports found for namespace import in ${targetFile.signature.fileName}"
                )
            }
        }

        EtsImportType.SIDE_EFFECT -> {
            // Side effect imports don't import specific symbols
            // Create a synthetic export to represent the side effect
            val sideEffectExport = EtsExportInfo(
                name = "",
                type = EtsExportType.UNKNOWN,
                from = null,
                nameBeforeAs = null,
            )
            SymbolResolutionResult.Success(targetFile, sideEffectExport)
        }
    }
}

fun normalizeRelativePath(path: String): String {
    val parts = path.split("/")
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
