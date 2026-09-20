package org.usvm.census

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrGenerationException
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class UnknownCallCensusRunner(
    private val manifest: UnknownCallCensusManifest,
    private val manifestPath: Path,
    private val checkoutRoot: Path,
    private val outputDirectory: Path,
) {
    fun run(): UnknownCallCensusSummary {
        validateManifest()
        outputDirectory.createDirectories()
        val rawOutput = outputDirectory.resolve(RAW_FILE_NAME)
        val summaryOutput = outputDirectory.resolve(SUMMARY_FILE_NAME)
        require(!rawOutput.exists() && !summaryOutput.exists()) {
            "Census output already contains raw.jsonl or summary.json: $outputDirectory"
        }

        CensusRecordWriter(rawOutput).use { writer ->
            val startedAt = Instant.now()
            writer.write(runStartRecord(startedAt))

            manifest.profiles.forEach { profile ->
                manifest.projects.forEach { project -> runProject(project, profile, writer) }
            }

            writer.write(
                buildJsonObject {
                    put("kind", "run_result")
                    put("schemaVersion", CENSUS_SCHEMA_VERSION)
                    put("startedAt", startedAt.toString())
                    put("finishedAt", Instant.now().toString())
                    put("status", "completed")
                }
            )
        }

        val summary = Files.newBufferedReader(rawOutput, StandardCharsets.UTF_8).useLines { lines ->
            UnknownCallCensusAggregator.summarize(lines)
        }
        Files.writeString(
            summaryOutput,
            censusJson.encodeToString(summary) + "\n",
            StandardCharsets.UTF_8,
        )

        return summary
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runProject(
        project: UnknownCallCensusProject,
        profile: UnknownCallCensusProfile,
        writer: CensusRecordWriter,
    ) {
        val projectStart = TimeSource.Monotonic.markNow()
        val projectTimeout = manifest.limits.projectTimeoutSeconds.seconds

        try {
            // A checkout entry may itself be a symlink. Its validated Git repository is the provenance boundary.
            val projectRoot = canonicalProjectCheckout(
                checkoutRoot = checkoutRoot,
                relativePath = project.path,
            )
            canonicalExistingProjectPath(
                projectRoot = projectRoot,
                relativePath = project.licenseFile,
                kind = "Project license file",
                requireDirectory = false,
            )

            validateRevision(
                projectRoot = projectRoot,
                expectedRevision = project.revision,
                timeout = remainingProjectBudget(projectStart, projectTimeout, phase = "Git revision validation"),
            )
            validateCleanCheckout(
                projectRoot = projectRoot,
                timeout = remainingProjectBudget(projectStart, projectTimeout, phase = "Git status validation"),
            )

            val loadedFiles = loadProjectFiles(project, projectRoot, projectStart, projectTimeout)
            val selection = selectCensusMethods(
                manifest = manifest,
                project = project,
                projectRoot = projectRoot,
                loadedFiles = loadedFiles,
            )
            val sceneFiles = loadedFiles.files
                .distinctBy { file -> requireNotNull(loadedFiles.pathsBySignature[file.signature]) }
                .sortedBy { file -> requireNotNull(loadedFiles.pathsBySignature[file.signature]) }
            val scene = EtsScene(
                projectFiles = sceneFiles,
                projectName = project.id,
            )
            val methods = selection.methods

            var rawEvents = 0
            var completedMethods = 0
            var partialMethods = 0
            var timedOutMethods = 0
            var failedMethods = 0
            var projectTimedOut = false

            for (method in methods) {
                if (projectStart.elapsedNow() >= projectTimeout) {
                    projectTimedOut = true
                    break
                }

                val result = runMethod(
                    project = project,
                    projectRoot = projectRoot,
                    scene = scene,
                    method = method,
                    profile = profile,
                    pathsBySignature = loadedFiles.pathsBySignature,
                    writer = writer,
                )
                rawEvents += result.events
                when (result.status) {
                    MethodStatus.COMPLETED -> completedMethods++
                    MethodStatus.PARTIAL -> partialMethods++
                    MethodStatus.TIMEOUT -> timedOutMethods++
                    MethodStatus.TOOL_ERROR -> failedMethods++
                }
            }

            writer.write(
                buildJsonObject {
                    putCommonProjectFields(project, profile)
                    put("kind", "project_result")
                    put("status", if (projectTimedOut) "timeout" else "completed")
                    put("sceneFiles", sceneFiles.size)
                    put("candidateFiles", selection.candidateFiles)
                    put("eligibleClasses", selection.eligibleClasses)
                    put("classesSelected", selection.selectedClasses)
                    put("methodsSelected", methods.size)
                    put("methodsCompleted", completedMethods)
                    put("methodsPartial", partialMethods)
                    put("methodsTimedOut", timedOutMethods)
                    put("methodsFailed", failedMethods)
                    put("rawEvents", rawEvents)
                    put("durationMillis", projectStart.elapsedNow().inWholeMilliseconds)
                    if (projectTimedOut) {
                        put("error", "Project timeout reached before all selected methods were analyzed")
                    }
                }
            )
        } catch (error: ProjectTimeoutException) {
            writeProjectFailure(project, profile, writer, "timeout", projectStart, error)
        } catch (error: Exception) {
            writeProjectFailure(project, profile, writer, "tool_error", projectStart, error)
        }
    }

    private fun writeProjectFailure(
        project: UnknownCallCensusProject,
        profile: UnknownCallCensusProfile,
        writer: CensusRecordWriter,
        status: String,
        projectStart: TimeSource.Monotonic.ValueTimeMark,
        error: Throwable,
    ) {
        writer.write(
            buildJsonObject {
                putCommonProjectFields(project, profile)
                put("kind", "project_result")
                put("status", status)
                put("durationMillis", projectStart.elapsedNow().inWholeMilliseconds)
                put("error", boundedError(error))
            }
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runMethod(
        project: UnknownCallCensusProject,
        projectRoot: Path,
        scene: EtsScene,
        method: EtsMethod,
        profile: UnknownCallCensusProfile,
        pathsBySignature: Map<EtsFileSignature, String>,
        writer: CensusRecordWriter,
    ): MethodRunResult {
        val entryFunctionId = functionId(project.id, projectRoot, method.signature, pathsBySignature)
        val observer = RecordingCensusObserver(
            project = project,
            projectRoot = projectRoot,
            profile = profile,
            entryFunctionId = entryFunctionId,
            pathsBySignature = pathsBySignature,
            writer = writer,
        )
        val methodTimeout = manifest.limits.methodTimeoutSeconds.seconds
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(CENSUS_PATH_SELECTION_STRATEGY),
            randomSeed = manifest.randomSeed,
            stopOnCoverage = CENSUS_STOP_ON_COVERAGE,
            timeout = methodTimeout,
            solverType = SolverType.YICES,
            throwExceptionOnStepFailure = true,
        )
        val emptyModelSelection = TsUnknownCallModelSelection.Only(emptySet())
        val tsOptions = TsOptions(
            unknownCallModelSelection = emptyModelSelection,
            unknownCallFallback = profile.fallback,
        )

        val analysisStart = TimeSource.Monotonic.markNow()
        var status = MethodStatus.COMPLETED
        var errorText: String? = null
        var failureCount = 0

        try {
            TsMachine(
                scene = scene,
                options = machineOptions,
                tsOptions = tsOptions,
                observer = observer,
            ).use { machine ->
                try {
                    machine.analyze(methods = listOf(method))
                } catch (error: Exception) {
                    status = MethodStatus.PARTIAL
                    failureCount++
                    errorText = boundedError(error)
                } catch (error: NotImplementedError) {
                    status = MethodStatus.PARTIAL
                    failureCount++
                    errorText = boundedError(error)
                }
            }

            if (observer.recordingFailureCount > 0) {
                failureCount += observer.recordingFailureCount
                errorText = combineErrors(errorText, observer.recordingFailureMessage)
                if (status == MethodStatus.COMPLETED) {
                    status = MethodStatus.PARTIAL
                }
            }

            val finalOutcome = methodOutcomeAfterTimeoutCheck(
                status = status,
                error = errorText,
                elapsed = analysisStart.elapsedNow(),
                timeout = methodTimeout,
            )
            status = finalOutcome.status
            errorText = finalOutcome.error
        } catch (error: Exception) {
            status = MethodStatus.TOOL_ERROR
            failureCount++
            errorText = boundedError(error)
        } catch (error: NotImplementedError) {
            status = MethodStatus.TOOL_ERROR
            failureCount++
            errorText = boundedError(error)
        }

        writer.write(
            buildJsonObject {
                putCommonProjectFields(project, profile)
                put("kind", "method_result")
                put("functionId", entryFunctionId)
                put("status", status.serializedName)
                put("durationMillis", analysisStart.elapsedNow().inWholeMilliseconds)
                put("rawEvents", observer.eventCount)
                put("failureCount", failureCount)
                errorText?.let { put("error", it) }
            }
        )

        return MethodRunResult(status = status, events = observer.eventCount)
    }

    private fun loadProjectFiles(
        project: UnknownCallCensusProject,
        projectRoot: Path,
        projectStart: TimeSource.Monotonic.ValueTimeMark,
        projectTimeout: Duration,
    ): LoadedProjectFiles {
        val sourceRoots = project.include.ifEmpty { listOf("") }
        val files = mutableListOf<EtsFile>()
        val pathsBySignature = hashMapOf<EtsFileSignature, String>()

        sourceRoots.forEach { relativePath ->
            ensureWithinProjectTimeout(projectStart, projectTimeout)
            val remaining = projectTimeout - projectStart.elapsedNow()

            val sourceRoot = canonicalExistingProjectPath(
                projectRoot = projectRoot,
                relativePath = relativePath,
                kind = "Configured source root",
                requireDirectory = true,
            )
            val generatedIr = try {
                generateEtsIR(
                    projectPath = sourceRoot,
                    isProject = true,
                    loadEntrypoints = false,
                    timeout = remaining,
                    provider = EtsIrProvider.TS_FRONTEND,
                )
            } catch (error: EtsIrGenerationException) {
                ensureWithinProjectTimeout(projectStart, projectTimeout, cause = error)
                throw error
            }
            val loadedFiles = try {
                loadEtsProjectFromIR(
                    projectFilesPath = generatedIr,
                    sdkFilesPath = null,
                ).projectFiles
            } finally {
                generatedIr.toFile().deleteRecursively()
            }

            ensureWithinProjectTimeout(projectStart, projectTimeout)

            loadedFiles.forEach { file ->
                val sourcePath = repositoryRelativeSourcePath(
                    rawPath = file.signature.fileName,
                    sourceRoot = sourceRoot,
                    sourceRootRelativePath = relativePath,
                    projectRoot = projectRoot,
                )
                val previousPath = pathsBySignature.put(file.signature, sourcePath)
                require(previousPath == null || previousPath == sourcePath) {
                    "Conflicting source paths for ${file.signature}: $previousPath and $sourcePath"
                }
            }
            files += loadedFiles
        }

        return LoadedProjectFiles(files = files, pathsBySignature = pathsBySignature)
    }

    private fun ensureWithinProjectTimeout(
        projectStart: TimeSource.Monotonic.ValueTimeMark,
        projectTimeout: Duration,
        cause: Throwable? = null,
    ) {
        if (projectStart.elapsedNow() >= projectTimeout) {
            throw ProjectTimeoutException("Project timeout reached during frontend conversion", cause)
        }
    }

    private fun remainingProjectBudget(
        projectStart: TimeSource.Monotonic.ValueTimeMark,
        projectTimeout: Duration,
        phase: String,
    ): Duration {
        val remaining = projectTimeout - projectStart.elapsedNow()
        if (remaining <= Duration.ZERO) {
            throw ProjectTimeoutException("Project timeout reached during $phase")
        }

        return remaining
    }

    private fun validateManifest() {
        require(manifest.schemaVersion == CENSUS_SCHEMA_VERSION) {
            "Unsupported census manifest schema ${manifest.schemaVersion}"
        }
        require(manifest.projects.isNotEmpty()) { "Census manifest must contain at least one project" }
        require(manifest.profiles.isNotEmpty()) { "Census manifest must contain at least one profile" }
        require(manifest.projects.map { it.id }.distinct().size == manifest.projects.size) {
            "Census project IDs must be unique"
        }
        require(manifest.profiles.distinct().size == manifest.profiles.size) {
            "Census profiles must be unique"
        }
        require(manifest.limits.projectTimeoutSeconds > 0) { "Project timeout must be positive" }
        require(manifest.limits.methodTimeoutSeconds > 0) { "Method timeout must be positive" }
        require(manifest.limits.maxClasses > 0) { "Class limit must be positive" }
        require(manifest.limits.maxMethods > 0) { "Method limit must be positive" }
        require(manifest.limits.minMethodsPerClass > 0) { "Minimum methods per class must be positive" }
        require(manifest.limits.minStatementsPerMethod > 0) { "Minimum statements per method must be positive" }
        val fullGitRevision = Regex("[0-9a-fA-F]{40}")
        manifest.projects.forEach { project ->
            require(project.id.isNotBlank()) { "Census project ID must not be blank" }
            require(project.repository.isNotBlank()) { "Census project repository must not be blank" }
            require(project.revision.matches(fullGitRevision)) {
                "Project ${project.id} must use a full 40-character Git revision"
            }
            require(project.path.isNotBlank()) { "Project ${project.id} checkout path must not be blank" }
            require(!Path.of(project.path).isAbsolute) { "Project ${project.id} checkout path must be relative" }
            require(project.license.isNotBlank()) { "Project ${project.id} license must not be blank" }
            require(project.licenseFile.isNotBlank()) { "Project ${project.id} license file must not be blank" }
            require(!Path.of(project.licenseFile).isAbsolute) {
                "Project ${project.id} license file must be relative"
            }
            require(project.include.none(String::isBlank)) { "Project ${project.id} source roots must not be blank" }
            require(project.includeSuffixes.isNotEmpty() && project.includeSuffixes.none(String::isBlank)) {
                "Project ${project.id} included source suffixes must not be empty or blank"
            }
        }
    }

    private fun runStartRecord(startedAt: Instant): JsonObject = buildJsonObject {
        put("kind", "run_start")
        put("schemaVersion", CENSUS_SCHEMA_VERSION)
        put("startedAt", startedAt.toString())
        put("manifestSha256", sha256(Files.readAllBytes(manifestPath)))
        put(
            "toolRevision",
            gitObject(Path.of("."), ref = "HEAD", timeout = RUN_METADATA_GIT_TIMEOUT) ?: "unknown",
        )
        put(
            "toolTree",
            gitObject(Path.of("."), ref = "HEAD^{tree}", timeout = RUN_METADATA_GIT_TIMEOUT) ?: "unknown",
        )
        put("jacodbArtifact", runtimeArtifactName(EtsScene::class.java))
        put("frontendProvider", EtsIrProvider.TS_FRONTEND.name)
        put("solver", SolverType.YICES.name)
        put("javaVersion", System.getProperty("java.version"))
        put("pathSelectionStrategy", CENSUS_PATH_SELECTION_STRATEGY.name)
        put("randomSeed", manifest.randomSeed)
        put("stopOnCoverage", CENSUS_STOP_ON_COVERAGE)
        put("unknownCallModelSelection", "NONE")
        put("legacyApproximationPolicy", "UNCHANGED")
        put("projectTimeoutSeconds", manifest.limits.projectTimeoutSeconds)
        put("methodTimeoutSeconds", manifest.limits.methodTimeoutSeconds)
        put("maxClasses", manifest.limits.maxClasses)
        put("maxMethods", manifest.limits.maxMethods)
        put("minMethodsPerClass", manifest.limits.minMethodsPerClass)
        put("minStatementsPerMethod", manifest.limits.minStatementsPerMethod)
    }

    private fun validateRevision(projectRoot: Path, expectedRevision: String, timeout: Duration) {
        val actualRevision = gitObject(
            directory = projectRoot,
            ref = "HEAD",
            timeout = timeout,
            timeoutFailure = {
                ProjectTimeoutException("Project timeout reached during Git revision validation")
            },
        )
            ?: error("Cannot read Git revision for project checkout $projectRoot")
        require(actualRevision.equals(expectedRevision, ignoreCase = true)) {
            "Project checkout $projectRoot is at $actualRevision, expected $expectedRevision"
        }
    }

    private fun validateCleanCheckout(projectRoot: Path, timeout: Duration) {
        val status = gitOutput(
            directory = projectRoot,
            arguments = listOf("status", "--porcelain=v1", "--untracked-files=normal"),
            timeout = timeout,
            timeoutFailure = {
                ProjectTimeoutException("Project timeout reached during Git status validation")
            },
        ) ?: error("Cannot inspect Git status for project checkout $projectRoot")
        require(status.isBlank()) { "Project checkout has tracked or untracked changes: $projectRoot" }
    }

    private fun gitObject(
        directory: Path,
        ref: String,
        timeout: Duration,
        timeoutFailure: (() -> ProjectTimeoutException)? = null,
    ): String? = gitOutput(
        directory = directory,
        arguments = listOf("rev-parse", ref),
        timeout = timeout,
        timeoutFailure = timeoutFailure,
    )

    private fun gitOutput(
        directory: Path,
        arguments: List<String>,
        timeout: Duration,
        timeoutFailure: (() -> ProjectTimeoutException)? = null,
    ): String? {
        val result = runCatching {
            boundedProcessOutput(
                command = listOf("git", "-C", directory.pathString) + arguments,
                timeout = timeout,
                maxOutputBytes = MAX_GIT_OUTPUT_BYTES,
            )
        }.getOrNull() ?: return null

        return when (result) {
            is BoundedProcessOutput.Completed -> {
                if (result.exitCode == 0 && !result.truncated) result.output.trim() else null
            }

            BoundedProcessOutput.TimedOut -> {
                timeoutFailure?.let { throw it() }
                null
            }
        }
    }

    private companion object {
        const val RAW_FILE_NAME = "raw.jsonl"
        const val SUMMARY_FILE_NAME = "summary.json"
        const val MAX_GIT_OUTPUT_BYTES = 64 * 1024
        val RUN_METADATA_GIT_TIMEOUT = 10.seconds
    }
}

private fun runtimeArtifactName(type: Class<*>): String = runCatching {
    Path.of(type.protectionDomain.codeSource.location.toURI()).name
}.getOrDefault("unknown")

private data class MethodRunResult(
    val status: MethodStatus,
    val events: Int,
)

internal data class LoadedProjectFiles(
    val files: List<EtsFile>,
    val pathsBySignature: Map<EtsFileSignature, String>,
)

private val UnknownCallCensusProfile.fallback: TsResidualCallPolicy
    get() = when (this) {
        UnknownCallCensusProfile.EMPTY_FRESH -> TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN
        UnknownCallCensusProfile.EMPTY_STOP -> TsResidualCallPolicy.STOP_PATH
    }
