package org.usvm.ts.pbt.fastcheck

import java.nio.file.Files
import java.nio.file.Path

/** Locates the private fast-check runtime in development and installed distributions. */
internal object FastCheckRuntime {
    fun executionEntryPoint(): Path = locateEntryPoint(EXECUTION_CLI)

    fun projectionEntryPoint(): Path = locateEntryPoint(PROJECTION_CLI)

    private fun locateEntryPoint(fileName: String): Path {
        val candidates = runtimeDirectories().map { runtimeDirectory ->
            runtimeDirectory.resolve(ENTRY_POINT_DIRECTORY).resolve(fileName)
        }
        return candidates.firstOrNull(Files::isRegularFile)
            ?: throw PbtBackendException(
                kind = BackendErrorKind.INVALID_REQUEST,
                code = "backend.runtime.not-found",
                message = "Cannot locate built fast-check adapter; checked $candidates",
            )
    }

    private fun runtimeDirectories(): List<Path> = listOfNotNull(
        configuredRuntimeDirectory(),
        installedRuntimeDirectory(),
    ).distinct()

    private fun configuredRuntimeDirectory(): Path? = System.getProperty(RUNTIME_DIRECTORY_PROPERTY)
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?.toAbsolutePath()
        ?.normalize()

    private fun installedRuntimeDirectory(): Path? {
        val location = FastCheckRuntime::class.java.protectionDomain.codeSource?.location ?: return null
        val codePath = runCatching { Path.of(location.toURI()) }.getOrNull() ?: return null
        val libraryDirectory = if (Files.isDirectory(codePath)) codePath else codePath.parent ?: return null

        return libraryDirectory.resolve(INSTALLED_RUNTIME_DIRECTORY)
    }

    private const val RUNTIME_DIRECTORY_PROPERTY = "org.usvm.ts.pbt.fastcheck.runtime"
    private const val ENTRY_POINT_DIRECTORY = "dist/src"
    private const val EXECUTION_CLI = "execution-cli.js"
    private const val PROJECTION_CLI = "projection-cli.js"
    private const val INSTALLED_RUNTIME_DIRECTORY = "fast-check-adapter"
}
