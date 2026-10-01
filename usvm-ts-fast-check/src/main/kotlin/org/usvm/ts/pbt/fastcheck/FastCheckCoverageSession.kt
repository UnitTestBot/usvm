package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.BranchCoverage
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.coverage.CoverageArtifactException
import org.usvm.ts.pbt.coverage.IstanbulCoverageContext
import org.usvm.ts.pbt.coverage.decodeIstanbulCoverageReport
import org.usvm.ts.pbt.coverage.inspectRawV8SourceMapDiagnostics
import org.usvm.ts.pbt.coverage.mergeCoverageDiagnostics
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

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

            val branchReport = if (finalArtifact.files.isEmpty()) {
                RawBranchReport(files = emptyList(), diagnostics = emptyList())
            } else {
                convertBranches()
            }
            appendExactBranches(
                artifact = finalArtifact.copy(diagnostics = diagnostics),
                report = branchReport,
            )
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

    private fun convertBranches(): RawBranchReport {
        val converter = workspace.adapterRoot.resolve("dist/src/coverage-branches.js")
        val transport = FastCheckProcessTransport(
            nodeExecutable = nodeExecutable,
            maxRequestBytes = 1,
            maxStdoutBytes = MAX_BRANCH_REPORT_BYTES,
            maxStderrBytes = MAX_BRANCH_ERROR_BYTES,
            shutdownGraceMillis = BRANCH_SHUTDOWN_GRACE_MILLIS,
        )
        val output = try {
            transport.invoke(
                command = listOf(nodeExecutable, converter.toString(), workspace.rawDirectory.toString()) +
                    request.sourceRoots,
                request = "",
                timeoutMillis = BRANCH_CONVERSION_TIMEOUT_MILLIS,
                reportedTimeoutMillis = BRANCH_CONVERSION_TIMEOUT_MILLIS,
                description = "TypeScript branch converter",
            )
        } catch (error: FastCheckTransportException) {
            return unsupportedBranches("Cannot convert TypeScript branches: ${error.message}")
        }
        if (output.exitCode != 0) {
            return unsupportedBranches("TypeScript branch conversion failed: ${output.stderr.trim()}")
        }

        return runCatching {
            PropertyManifestJson.json.decodeFromString<RawBranchReport>(output.stdout)
        }.getOrElse { error ->
            unsupportedBranches("Cannot read TypeScript branch coverage: ${error.message}")
        }
    }

    private fun appendExactBranches(
        artifact: PropertyCoverageArtifact,
        report: RawBranchReport,
    ): PropertyCoverageArtifact {
        val exactByPath = report.files.associate { file -> file.path to file.branches }
        val retainedPaths = artifact.files.mapTo(hashSetOf()) { file -> file.path }
        val files = artifact.files.map { file ->
            val nextId = (file.branches.maxOfOrNull(BranchCoverage::branchId) ?: -1) + 1
            val exactBranches = exactByPath[file.path].orEmpty().mapIndexed { index, branch ->
                branch.copy(branchId = nextId + index)
            }

            file.copy(branches = file.branches + exactBranches)
        }

        return artifact.copy(
            files = files,
            diagnostics = artifact.diagnostics + report.diagnostics.filter { diagnostic ->
                diagnostic.path == null || diagnostic.path in retainedPaths
            },
        )
    }

    private fun unsupportedBranches(message: String) = RawBranchReport(
        files = emptyList(),
        diagnostics = listOf(
            CoverageDiagnostic(
                code = "coverage.branch.unsupported",
                message = message,
            ),
        ),
    )

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
                    code = FastCheckDiagnosticCode.COVERAGE_COLLECTOR_NOT_FOUND,
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
            val root = Files.createTempDirectory("usvm-ts-fast-check-coverage-")

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
            val transport = FastCheckProcessTransport(
                nodeExecutable = nodeExecutable,
                maxRequestBytes = 1,
                maxStdoutBytes = MAX_VERSION_OUTPUT_BYTES,
                maxStderrBytes = MAX_VERSION_OUTPUT_BYTES,
                shutdownGraceMillis = VERSION_SHUTDOWN_GRACE_MILLIS,
                useProcessSupervisor = false,
            )
            val output = try {
                transport.invoke(
                    command = listOf(nodeExecutable, "--version"),
                    request = "",
                    timeoutMillis = NODE_VERSION_TIMEOUT_MILLIS,
                    reportedTimeoutMillis = NODE_VERSION_TIMEOUT_MILLIS,
                    description = "Node.js version probe",
                )
            } catch (error: FastCheckTransportException) {
                val interrupted = error.code == FastCheckDiagnosticCode.BACKEND_PROCESS_INTERRUPTED
                failPreparation(
                    request = request,
                    code = if (interrupted) {
                        error.code
                    } else {
                        FastCheckDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE
                    },
                    message = "Cannot query the Node.js runtime version: ${error.message}",
                    kind = if (interrupted) BackendErrorKind.PROCESS_FAILURE else BackendErrorKind.COVERAGE,
                    cause = error,
                )
            }
            val version = output.stdout.trim()
            if (output.exitCode != 0 || version.isBlank()) {
                failPreparation(
                    request = request,
                    code = FastCheckDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                    message = "Cannot query the Node.js runtime version",
                )
            }

            return requireSupportedNodeVersion(version, request)
        }

        private fun requireSupportedNodeVersion(version: String, request: FastCheckExecutionRequest): String {
            val match = NODE_VERSION_PATTERN.matchEntire(version)
            val major = match?.groupValues?.get(1)?.toIntOrNull()
            val minor = match?.groupValues?.get(2)?.toIntOrNull()
            if (major == null || minor == null) {
                failPreparation(
                    request = request,
                    code = FastCheckDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                    message = "Cannot parse the Node.js runtime version: $version",
                )
            }

            val hasNewerMajorVersion = major > MINIMUM_NODE_MAJOR_VERSION
            val hasMinimumVersion = major == MINIMUM_NODE_MAJOR_VERSION && minor >= MINIMUM_NODE_MINOR_VERSION
            val supported = hasNewerMajorVersion || hasMinimumVersion
            if (!supported) {
                failPreparation(
                    request = request,
                    code = FastCheckDiagnosticCode.COVERAGE_RUNTIME_UNSUPPORTED,
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
        private const val VERSION_SHUTDOWN_GRACE_MILLIS = 100L
        private const val MAX_VERSION_OUTPUT_BYTES = 1_000
        private const val BRANCH_CONVERSION_TIMEOUT_MILLIS = 10_000L
        private const val BRANCH_SHUTDOWN_GRACE_MILLIS = 500L
        private const val MAX_BRANCH_REPORT_BYTES = 10_000_000
        private const val MAX_BRANCH_ERROR_BYTES = 1_000
        private const val MINIMUM_NODE_MAJOR_VERSION = 18
        private const val MINIMUM_NODE_MINOR_VERSION = 18
        private val NODE_VERSION_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
    }
}

@Serializable
private data class RawBranchReport(
    val files: List<RawBranchFile>,
    val diagnostics: List<CoverageDiagnostic>,
)

@Serializable
private data class RawBranchFile(
    val path: String,
    val branches: List<BranchCoverage>,
)

private data class CoverageWorkspace(
    val root: Path,
    val configPath: Path,
    val rawDirectory: Path,
    val reportDirectory: Path,
    val c8EntryPoint: Path,
    val adapterRoot: Path,
)
