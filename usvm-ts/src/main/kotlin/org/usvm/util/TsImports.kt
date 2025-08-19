package org.usvm.util

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import java.nio.file.Path
import kotlin.io.path.toPath

sealed class ImportResolutionResult {
    data class Success(val file: EtsFile) : ImportResolutionResult()
    data class NotFound(val reason: String) : ImportResolutionResult()
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
        ImportResolutionResult.NotFound(e.message ?: "Unknown error")
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

fun normalizeRelativePath(path: String): String {
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
