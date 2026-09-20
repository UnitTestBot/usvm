package org.usvm.ts.calls

import java.nio.file.Files
import java.nio.file.Path

internal object CallsReplayRuntime {
    fun sourceTargetReplayEntryPoint(): Path = locateEntryPoint(SOURCE_TARGET_REPLAY_CLI)

    fun processSupervisorEntryPoint(): Path = locateEntryPoint(PROCESS_SUPERVISOR)

    private fun locateEntryPoint(fileName: String): Path {
        val candidates = runtimeDirectories().map { runtimeDirectory ->
            runtimeDirectory.resolve(ENTRY_POINT_DIRECTORY).resolve(fileName)
        }

        return candidates.firstOrNull(Files::isRegularFile)
            ?: error("Cannot locate built TS Calls source replay adapter; checked $candidates")
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
        val location = CallsReplayRuntime::class.java.protectionDomain.codeSource?.location ?: return null
        val codePath = runCatching { Path.of(location.toURI()) }.getOrNull() ?: return null
        val libraryDirectory = if (Files.isDirectory(codePath)) codePath else codePath.parent ?: return null

        return libraryDirectory.resolve(INSTALLED_RUNTIME_DIRECTORY)
    }

    private const val RUNTIME_DIRECTORY_PROPERTY = "org.usvm.ts.calls.replay.runtime"
    private const val ENTRY_POINT_DIRECTORY = "dist/src"
    private const val SOURCE_TARGET_REPLAY_CLI = "source-target-replay-cli.js"
    private const val PROCESS_SUPERVISOR = "process-supervisor.js"
    private const val INSTALLED_RUNTIME_DIRECTORY = "source-replay-adapter"
}
