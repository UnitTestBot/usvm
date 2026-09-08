package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.coverage.CoverageArtifactException
import org.usvm.ts.pbt.coverage.IstanbulCoverageContext
import org.usvm.ts.pbt.coverage.decodeIstanbulCoverageReport
import org.usvm.ts.pbt.coverage.inspectRawV8SourceMapDiagnostics
import org.usvm.ts.pbt.coverage.mergeCoverageDiagnostics
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Prepared c8 invocation and the temporary artifacts produced by one property run. */
internal class FastCheckCoverageSession private constructor(
    private val nodeExecutable: String,
    private val adapterEntryPoint: Path,
    private val request: FastCheckExecutionRequest,
    private val runtimeVersion: String,
    private val workspace: CoverageWorkspace,
) : AutoCloseable {
    val adapterCommand: List<String>
        get() = buildList {
            add(nodeExecutable)
            add(workspace.c8EntryPoint.toString())
            add("--config=${workspace.configPath}")
            add("--reporter=json")
            add("--reports-dir=${workspace.reportDirectory}")
            add("--temp-directory=${workspace.rawDirectory}")
            add("--exclude-after-remap")
            add("--allowExternal")
            add("--exclude=__usvm_no_default_excludes__")
            if (CoverageScope.DEPENDENCIES in requireNotNull(request.coverageRequest).scopes) {
                add("--exclude-node-modules=false")
            }
            add(nodeExecutable)
            add(adapterEntryPoint.toString())
        }

    fun attachTo(result: PropertyRunResult): PropertyRunResult {
        val coverageRequest = requireNotNull(request.coverageRequest)
        val entryPointPaths = propertyEntryPointPaths()

        val artifact = try {
            val context = IstanbulCoverageContext(
                backendId = FastCheckBackend.FAST_CHECK_BACKEND_ID,
                backendVersion = FastCheckRuntimeMetadata.fastCheckVersion,
                propertyId = result.propertyId,
                sourceRoots = request.sourceRoots,
                propertyEntryPointPaths = entryPointPaths,
                adapterRoot = workspace.adapterRoot.toString(),
                runtimeVersion = runtimeVersion,
                collector = FastCheckRuntimeMetadata.coverageCollector,
                request = coverageRequest,
            )
            val finalArtifact = decodeIstanbulCoverageReport(
                reportPath = workspace.reportDirectory.resolve("coverage-final.json"),
                context = context,
            )
            val rawDiagnostics = inspectRawV8SourceMapDiagnostics(
                rawDirectory = workspace.rawDirectory,
                sourceRoots = request.sourceRoots,
            )
            val diagnostics = mergeCoverageDiagnostics(
                finalDiagnostics = finalArtifact.diagnostics,
                rawDiagnostics = rawDiagnostics,
            )

            finalArtifact.copy(diagnostics = diagnostics)
        } catch (error: CoverageArtifactException) {
            fail(
                code = error.diagnostic.code,
                message = error.diagnostic.message,
                path = error.diagnostic.path,
                cause = error,
            )
        }

        return result.copy(coverage = artifact)
    }

    private fun propertyEntryPointPaths(): Set<String> {
        val paths = hashSetOf<String>()
        val entryPoints = listOfNotNull(request.manifest.predicate, request.manifest.precondition)

        for (sourceRoot in request.sourceRoots) {
            val root = Path.of(sourceRoot)
            for (entryPoint in entryPoints) {
                val entryPointPath = root.resolve(entryPoint.module).normalize()
                paths += canonicalizeExistingEntryPoint(entryPointPath)
            }
        }

        return paths
    }

    override fun close() {
        workspace.root.toFile().deleteRecursively()
    }

    private fun canonicalizeExistingEntryPoint(candidate: Path): String =
        if (Files.exists(candidate)) candidate.toRealPath().toString() else candidate.toString()

    private fun fail(
        code: String,
        message: String,
        path: String? = null,
        cause: Throwable? = null,
    ): Nothing = throw PbtBackendException(
        kind = BackendErrorKind.COVERAGE,
        code = code,
        message = message,
        propertyId = request.manifest.propertyId,
        path = path,
        cause = cause,
    )

    internal companion object {
        fun prepare(
            nodeExecutable: String,
            adapterEntryPoint: Path,
            request: FastCheckExecutionRequest,
        ): FastCheckCoverageSession {
            val runtimeVersion = readSupportedNodeVersion(nodeExecutable, request)
            val adapterRoot = adapterEntryPoint.parent?.parent?.parent
                ?: throw IllegalArgumentException("Adapter entry point has no runtime root: $adapterEntryPoint")
            val c8EntryPoint = adapterRoot.resolve("node_modules/c8/bin/c8.js")
            if (!Files.isRegularFile(c8EntryPoint)) {
                failPreparation(
                    request = request,
                    code = PbtDiagnosticCode.COVERAGE_COLLECTOR_NOT_FOUND,
                    message = "Cannot locate c8 ${FastCheckRuntimeMetadata.coverageCollector.version} " +
                        "in the fast-check adapter runtime",
                    path = c8EntryPoint.toString(),
                )
            }

            val workspace = createWorkspace(c8EntryPoint, adapterRoot)

            return FastCheckCoverageSession(
                nodeExecutable = nodeExecutable,
                adapterEntryPoint = adapterEntryPoint,
                request = request,
                runtimeVersion = runtimeVersion,
                workspace = workspace,
            )
        }

        private fun createWorkspace(c8EntryPoint: Path, adapterRoot: Path): CoverageWorkspace {
            val root = Files.createTempDirectory("usvm-ts-pbt-coverage-")

            try {
                val configPath = Files.writeString(root.resolve("c8-config.json"), "{}")

                return CoverageWorkspace(
                    root = root,
                    configPath = configPath,
                    rawDirectory = root.resolve("raw"),
                    reportDirectory = root.resolve("report"),
                    c8EntryPoint = c8EntryPoint,
                    adapterRoot = adapterRoot,
                )
            } catch (error: IOException) {
                root.toFile().deleteRecursively()
                throw error
            }
        }

        private fun readSupportedNodeVersion(
            nodeExecutable: String,
            request: FastCheckExecutionRequest,
        ): String {
            val process = try {
                ProcessBuilder(nodeExecutable, "--version").start()
            } catch (error: IOException) {
                failPreparation(
                    request = request,
                    code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                    message = "Cannot query the Node.js runtime version: ${error.message}",
                    cause = error,
                )
            }

            try {
                awaitNodeVersion(process, request)
                val version = process.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                    input.readLine().orEmpty().trim()
                }
                if (process.exitValue() != 0 || version.isBlank()) {
                    failPreparation(
                        request = request,
                        code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                        message = "Cannot query the Node.js runtime version",
                    )
                }

                return requireSupportedNodeVersion(version, request)
            } finally {
                runCatching { process.outputStream.close() }
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
            }
        }

        private fun awaitNodeVersion(process: Process, request: FastCheckExecutionRequest) {
            val completed = try {
                process.waitFor(NODE_VERSION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                failPreparation(
                    request = request,
                    code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
                    message = "Interrupted while querying the Node.js runtime version",
                    kind = BackendErrorKind.PROCESS_FAILURE,
                    cause = error,
                )
            }
            if (!completed) {
                process.destroyForcibly()
                failPreparation(
                    request = request,
                    code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                    message = "Timed out while querying the Node.js runtime version",
                )
            }
        }

        private fun requireSupportedNodeVersion(version: String, request: FastCheckExecutionRequest): String {
            val match = NODE_VERSION_PATTERN.matchEntire(version)
            val major = match?.groupValues?.get(1)?.toIntOrNull()
            val minor = match?.groupValues?.get(2)?.toIntOrNull()
            if (major == null || minor == null) {
                failPreparation(
                    request = request,
                    code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                    message = "Cannot parse the Node.js runtime version: $version",
                )
            }

            val hasNewerMajorVersion = major > MINIMUM_NODE_MAJOR_VERSION
            val hasMinimumVersion = major == MINIMUM_NODE_MAJOR_VERSION && minor >= MINIMUM_NODE_MINOR_VERSION
            val supported = hasNewerMajorVersion || hasMinimumVersion
            if (!supported) {
                failPreparation(
                    request = request,
                    code = PbtDiagnosticCode.COVERAGE_RUNTIME_UNSUPPORTED,
                    message = "Coverage requires Node.js 18.18 or newer; found $version",
                )
            }

            return version
        }

        private fun failPreparation(
            request: FastCheckExecutionRequest,
            code: String,
            message: String,
            kind: BackendErrorKind = BackendErrorKind.COVERAGE,
            path: String? = null,
            cause: Throwable? = null,
        ): Nothing = throw PbtBackendException(
            kind = kind,
            code = code,
            message = message,
            propertyId = request.manifest.propertyId,
            path = path,
            cause = cause,
        )

        private const val NODE_VERSION_TIMEOUT_MILLIS = 5_000L
        private const val MINIMUM_NODE_MAJOR_VERSION = 18
        private const val MINIMUM_NODE_MINOR_VERSION = 18
        private val NODE_VERSION_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
    }
}

private data class CoverageWorkspace(
    val root: Path,
    val configPath: Path,
    val rawDirectory: Path,
    val reportDirectory: Path,
    val c8EntryPoint: Path,
    val adapterRoot: Path,
)
