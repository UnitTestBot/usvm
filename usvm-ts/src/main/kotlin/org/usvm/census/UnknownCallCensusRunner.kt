package org.usvm.census

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsSourceSpan
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.EtsIrGenerationException
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
            val selection = selectMethods(project, projectRoot, loadedFiles)
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

    private fun selectMethods(
        project: UnknownCallCensusProject,
        projectRoot: Path,
        loadedFiles: LoadedProjectFiles,
    ): SelectedMethods {
        val candidateFiles = loadedFiles.files.asSequence()
            .map { file -> file to requireNotNull(loadedFiles.pathsBySignature[file.signature]) }
            .filter { (_, fileName) -> project.includeSuffixes.any(fileName::endsWith) }
            .filterNot { (_, fileName) -> project.excludeSuffixes.any(fileName::endsWith) }
            .distinctBy { (_, fileName) -> fileName }
            .sortedBy { (_, fileName) -> fileName }
            .map { (file, _) -> file }
            .toList()

        val eligibleClasses = candidateFiles
            .flatMap { file -> file.allClasses }
            .map { clazz ->
                val classId = classId(project.id, projectRoot, clazz.signature, loadedFiles.pathsBySignature)
                val methods = clazz.methods
                    .asSequence()
                    .filterNot { method -> method.cfg.stmts.isEmpty() }
                    .filterNot { method -> method.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                    .filterNot { method -> method.name == DEFAULT_ARK_METHOD_NAME }
                    .filterNot { method -> method.name == INSTANCE_INIT_METHOD_NAME }
                    .filterNot { method -> method.name == STATIC_INIT_METHOD_NAME }
                    .sortedWith(
                        compareBy(
                            { method ->
                                stableSelectionRank(
                                    seed = manifest.randomSeed,
                                    identity = functionId(
                                        project.id,
                                        projectRoot,
                                        method.signature,
                                        loadedFiles.pathsBySignature,
                                    ),
                                )
                            },
                            { method ->
                                functionId(
                                    project.id,
                                    projectRoot,
                                    method.signature,
                                    loadedFiles.pathsBySignature,
                                )
                            },
                        )
                    )
                    .toList()

                SelectedClass(classId = classId, methods = methods)
            }
            .filter { selectedClass ->
                selectedClass.methods.count { method ->
                    method.cfg.stmts.size >= manifest.limits.minStatementsPerMethod
                } >= manifest.limits.minMethodsPerClass
            }

        val selectedClasses = eligibleClasses
            .sortedWith(
                compareBy(
                    { selectedClass -> stableSelectionRank(manifest.randomSeed, selectedClass.classId) },
                    SelectedClass::classId,
                )
            )
            .take(manifest.limits.maxClasses)
        val selectedMethods = roundRobin(selectedClasses.map(SelectedClass::methods))
            .take(manifest.limits.maxMethods)

        return SelectedMethods(
            candidateFiles = candidateFiles.size,
            eligibleClasses = eligibleClasses.size,
            selectedClasses = selectedClasses.size,
            methods = selectedMethods,
        )
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

internal fun canonicalProjectCheckout(checkoutRoot: Path, relativePath: String): Path {
    val canonicalCheckoutRoot = checkoutRoot.toRealPath()
    val configuredProjectRoot = canonicalCheckoutRoot.resolve(relativePath).normalize()
    require(configuredProjectRoot.startsWith(canonicalCheckoutRoot)) {
        "Project checkout escapes the configured checkout root: $configuredProjectRoot"
    }
    require(configuredProjectRoot.isDirectory()) {
        "Project checkout does not exist: $configuredProjectRoot"
    }

    return configuredProjectRoot.toRealPath()
}

internal fun canonicalExistingProjectPath(
    projectRoot: Path,
    relativePath: String,
    kind: String,
    requireDirectory: Boolean,
): Path {
    val canonicalProjectRoot = projectRoot.toRealPath()
    val configuredPath = canonicalProjectRoot.resolve(relativePath).normalize()
    require(configuredPath.startsWith(canonicalProjectRoot)) {
        "$kind escapes project checkout: $configuredPath"
    }
    require(configuredPath.exists()) { "$kind does not exist: $relativePath" }
    if (requireDirectory) {
        require(configuredPath.isDirectory()) { "$kind is not a directory: $configuredPath" }
    }

    val canonicalPath = configuredPath.toRealPath()
    require(canonicalPath.startsWith(canonicalProjectRoot)) {
        "$kind escapes project checkout through a symbolic link: $configuredPath"
    }

    return canonicalPath
}

internal sealed interface BoundedProcessOutput {
    data class Completed(
        val exitCode: Int,
        val output: String,
        val truncated: Boolean,
    ) : BoundedProcessOutput

    data object TimedOut : BoundedProcessOutput
}

internal fun boundedProcessOutput(
    command: List<String>,
    timeout: Duration,
    maxOutputBytes: Int,
): BoundedProcessOutput {
    require(timeout > Duration.ZERO) { "Process timeout must be positive" }
    require(maxOutputBytes > 0) { "Process output limit must be positive" }

    var process: Process? = null
    var outputReader: Thread? = null
    try {
        val processStart = TimeSource.Monotonic.markNow()
        val startedProcess = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        process = startedProcess
        startedProcess.outputStream.close()

        val outputCollector = BoundedOutputCollector(maxOutputBytes)
        val startedOutputReader = Thread(
            { outputCollector.drain(startedProcess.inputStream) },
            "unknown-call-census-output-reader",
        ).apply {
            isDaemon = true
            start()
        }
        outputReader = startedOutputReader

        val remaining = timeout - processStart.elapsedNow()
        val completed = remaining > Duration.ZERO && startedProcess.waitFor(
            remaining.inWholeMilliseconds.coerceAtLeast(minimumValue = 1),
            TimeUnit.MILLISECONDS,
        )
        if (!completed) {
            terminateProcessTreeBestEffort(startedProcess)
            closeProcessOutputBestEffort(startedProcess)
            joinBestEffort(startedOutputReader, PROCESS_TERMINATION_GRACE)
            return BoundedProcessOutput.TimedOut
        }

        val outputRead = joinWithinTimeout(
            thread = startedOutputReader,
            timeout = timeout - processStart.elapsedNow(),
        )
        if (!outputRead) {
            closeProcessOutputBestEffort(startedProcess)
            joinBestEffort(startedOutputReader, PROCESS_TERMINATION_GRACE)
            return BoundedProcessOutput.TimedOut
        }

        outputCollector.failure?.let { throw it }
        return BoundedProcessOutput.Completed(
            exitCode = startedProcess.exitValue(),
            output = outputCollector.output(),
            truncated = outputCollector.truncated,
        )
    } finally {
        process?.takeIf(Process::isAlive)?.let(::terminateProcessTreeBestEffort)
        process?.let(::closeProcessOutputBestEffort)
        outputReader?.takeIf(Thread::isAlive)?.let { reader ->
            joinBestEffort(reader, PROCESS_TERMINATION_GRACE)
        }
    }
}

private class BoundedOutputCollector(private val maxOutputBytes: Int) {
    private val retainedOutput = ByteArray(maxOutputBytes)
    private var retainedBytes = 0

    var truncated: Boolean = false
        private set

    var failure: Exception? = null
        private set

    fun drain(input: InputStream) {
        val buffer = ByteArray(PROCESS_OUTPUT_BUFFER_BYTES)
        try {
            input.use {
                var readBytes = it.read(buffer)
                while (readBytes >= 0) {
                    retain(buffer, readBytes)
                    readBytes = it.read(buffer)
                }
            }
        } catch (error: IOException) {
            failure = error
        }
    }

    private fun retain(buffer: ByteArray, readBytes: Int) {
        val retainedFromChunk = minOf(readBytes, maxOutputBytes - retainedBytes)
        if (retainedFromChunk > 0) {
            buffer.copyInto(
                destination = retainedOutput,
                destinationOffset = retainedBytes,
                endIndex = retainedFromChunk,
            )
            retainedBytes += retainedFromChunk
        }
        if (retainedFromChunk < readBytes) {
            truncated = true
        }
    }

    fun output(): String = retainedOutput.copyOf(retainedBytes).toString(StandardCharsets.UTF_8)
}

private fun joinWithinTimeout(thread: Thread, timeout: Duration): Boolean {
    if (timeout <= Duration.ZERO) {
        return !thread.isAlive
    }

    thread.join(timeout.inWholeMilliseconds.coerceAtLeast(minimumValue = 1))
    return !thread.isAlive
}

private fun joinBestEffort(thread: Thread, timeout: Duration) {
    try {
        joinWithinTimeout(thread, timeout)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun closeProcessOutputBestEffort(process: Process) {
    runCatching { process.inputStream.close() }
}

private fun terminateProcessTree(process: Process) {
    val descendants = process.toHandle().descendants().use { handles ->
        handles.iterator().asSequence().toList()
    }
    val processTree = listOf(process.toHandle()) + descendants
    processTree.asReversed().forEach(ProcessHandle::destroy)

    if (!awaitTermination(processTree, PROCESS_TERMINATION_GRACE)) {
        processTree.asReversed()
            .filter(ProcessHandle::isAlive)
            .forEach(ProcessHandle::destroyForcibly)
        awaitTermination(processTree, PROCESS_TERMINATION_GRACE)
    }

    process.waitFor(PROCESS_TERMINATION_GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

private fun terminateProcessTreeBestEffort(process: Process) {
    try {
        terminateProcessTree(process)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        process.destroyForcibly()
    } catch (_: Exception) {
        process.destroyForcibly()
    }
}

private fun awaitTermination(processes: List<ProcessHandle>, timeout: Duration): Boolean {
    val exits = processes.map(ProcessHandle::onExit).toTypedArray()
    return try {
        CompletableFuture.allOf(*exits).get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        true
    } catch (_: TimeoutException) {
        false
    } catch (_: ExecutionException) {
        false
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
}

private val PROCESS_TERMINATION_GRACE = 500.milliseconds
private const val PROCESS_OUTPUT_BUFFER_BYTES = 8 * 1024

private fun runtimeArtifactName(type: Class<*>): String = runCatching {
    Path.of(type.protectionDomain.codeSource.location.toURI()).name
}.getOrDefault("unknown")

private class RecordingCensusObserver(
    private val project: UnknownCallCensusProject,
    private val projectRoot: Path,
    private val profile: UnknownCallCensusProfile,
    private val entryFunctionId: String,
    private val pathsBySignature: Map<EtsFileSignature, String>,
    private val writer: CensusRecordWriter,
) : TsInterpreterObserver {
    var eventCount: Int = 0
        private set
    var recordingFailureCount: Int = 0
        private set
    var recordingFailureMessage: String? = null
        private set

    @Suppress("TooGenericExceptionCaught")
    override fun onUnknownCall(event: TsUnknownCallEvent) {
        try {
            val containingSignature = event.callSite.location.method.signature
            val functionId = functionId(project.id, projectRoot, containingSignature, pathsBySignature)
            val sourceSpan = event.callSite.location.origin
            val sourceFile = sourcePath(containingSignature, projectRoot, pathsBySignature)
            val siteId = siteId(functionId, sourceSpan, event.callSite.location.index)
            val decision = event.decision.serializedName
            val eventIndex = eventCount + 1

            writer.write(
                buildJsonObject {
                    putCommonProjectFields(project, profile)
                    put("kind", "unknown_call")
                    put("entryFunctionId", entryFunctionId)
                    put("functionId", functionId)
                    put("siteId", siteId)
                    put("sourceFile", sourceFile)
                    sourceSpan?.let { span ->
                        put("startLine", span.startLine)
                        put("startColumn", span.startColumn)
                        put("endLine", span.endLine)
                        put("endColumn", span.endColumn)
                    }
                    put("statementIndex", event.callSite.location.index)
                    put("calleeId", calleeId(projectRoot, event.callee, pathsBySignature))
                    put("calleeName", event.callee.name)
                    put("failureReason", event.failureReason.name)
                    put("decision", decision)
                    put("outcome", event.outcome.name)
                    put("eventIndex", eventIndex)
                }
            )
            eventCount = eventIndex
        } catch (error: Exception) {
            recordingFailureCount++
            if (recordingFailureMessage == null) {
                recordingFailureMessage = "Unknown-call event recording failed: ${boundedError(error)}"
            }
        }
    }
}

internal class CensusRecordWriter(output: Path) : AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(
        output,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )

    fun write(record: JsonObject) {
        writer.appendLine(record.toString())
        writer.flush()
    }

    override fun close() {
        writer.close()
    }
}

internal enum class MethodStatus(val serializedName: String) {
    COMPLETED("completed"),
    PARTIAL("partial"),
    TIMEOUT("timeout"),
    TOOL_ERROR("tool_error"),
}

internal data class MethodOutcome(
    val status: MethodStatus,
    val error: String?,
)

internal fun methodOutcomeAfterTimeoutCheck(
    status: MethodStatus,
    error: String?,
    elapsed: Duration,
    timeout: Duration,
): MethodOutcome = if (status == MethodStatus.COMPLETED && elapsed >= timeout) {
    MethodOutcome(
        status = MethodStatus.TIMEOUT,
        error = "Machine timeout reached",
    )
} else {
    MethodOutcome(status = status, error = error)
}

private data class MethodRunResult(
    val status: MethodStatus,
    val events: Int,
)

private data class LoadedProjectFiles(
    val files: List<EtsFile>,
    val pathsBySignature: Map<EtsFileSignature, String>,
)

private data class SelectedClass(
    val classId: String,
    val methods: List<EtsMethod>,
)

private data class SelectedMethods(
    val candidateFiles: Int,
    val eligibleClasses: Int,
    val selectedClasses: Int,
    val methods: List<EtsMethod>,
)

private val UnknownCallCensusProfile.fallback: TsResidualCallPolicy
    get() = when (this) {
        UnknownCallCensusProfile.EMPTY_FRESH -> TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN
        UnknownCallCensusProfile.EMPTY_STOP -> TsResidualCallPolicy.STOP_PATH
    }

private val TsUnknownCallDecision.serializedName: String
    get() = when (this) {
        is TsUnknownCallDecision.ModelApplied -> "MODEL_APPLIED:$modelId"
        is TsUnknownCallDecision.ResidualFallback -> "RESIDUAL_FALLBACK:${policy.name}"
    }

private fun JsonObjectBuilderScope.putCommonProjectFields(
    project: UnknownCallCensusProject,
    profile: UnknownCallCensusProfile,
) {
    put("schemaVersion", CENSUS_SCHEMA_VERSION)
    put("projectId", project.id)
    put("projectRevision", project.revision)
    put("profile", profile.name)
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder

private fun functionId(
    projectId: String,
    projectRoot: Path,
    signature: EtsMethodSignature,
    pathsBySignature: Map<EtsFileSignature, String>,
): String {
    val sourceFile = sourcePath(signature, projectRoot, pathsBySignature)
    return "$projectId:$sourceFile:${signatureKey(signature)}"
}

private fun classId(
    projectId: String,
    projectRoot: Path,
    signature: EtsClassSignature,
    pathsBySignature: Map<EtsFileSignature, String>,
): String {
    val sourceFile = sourcePath(signature.file, projectRoot, pathsBySignature)
    val namespace = signature.namespace?.qualifiedName()
    val className = listOfNotNull(namespace, signature.name).joinToString(separator = "::")
    return "$projectId:$sourceFile:$className"
}

private fun calleeId(
    projectRoot: Path,
    signature: EtsMethodSignature,
    pathsBySignature: Map<EtsFileSignature, String>,
): String {
    val sourceFile = sourcePath(signature, projectRoot, pathsBySignature)
    return "$sourceFile:${signatureKey(signature)}"
}

private fun sourcePath(
    signature: EtsMethodSignature,
    projectRoot: Path,
    pathsBySignature: Map<EtsFileSignature, String>,
): String = sourcePath(signature.enclosingClass.file, projectRoot, pathsBySignature)

private fun sourcePath(
    signature: EtsFileSignature,
    projectRoot: Path,
    pathsBySignature: Map<EtsFileSignature, String>,
): String = pathsBySignature[signature]
    ?: normalizedSourcePath(signature.fileName, projectRoot)

private fun signatureKey(signature: EtsMethodSignature): String {
    val parameters = signature.parameters.joinToString(separator = ",") { parameter -> parameter.type.toString() }
    val namespace = signature.enclosingClass.namespace?.qualifiedName()
    val className = listOfNotNull(namespace, signature.enclosingClass.name).joinToString(separator = "::")
    return "$className:${signature.name}($parameters)->${signature.returnType}"
}

private fun EtsNamespaceSignature.qualifiedName(): String =
    listOfNotNull(namespace?.qualifiedName(), name).joinToString(separator = "::")

private fun siteId(functionId: String, sourceSpan: EtsSourceSpan?, statementIndex: Int): String =
    if (sourceSpan == null) {
        "$functionId:ir-index:$statementIndex"
    } else {
        "$functionId:${sourceSpan.startLine}:${sourceSpan.startColumn}:${sourceSpan.endLine}:${sourceSpan.endColumn}"
    }

private fun normalizedSourcePath(rawPath: String, projectRoot: Path): String {
    val normalizedRawPath = rawPath.normalizedSeparators().removePrefix("@")
    val sourcePath = runCatching { Path.of(rawPath).normalize().absolute() }.getOrNull()
    val normalizedRoot = projectRoot.normalize().absolute()

    return when {
        sourcePath != null && sourcePath.startsWith(normalizedRoot) -> {
            normalizedRoot.relativize(sourcePath).pathString.normalizedSeparators()
        }

        Path.of(rawPath).isAbsolute -> "external/${Path.of(rawPath).name}"
        else -> normalizedRawPath.removePrefix("./")
    }
}

private fun repositoryRelativeSourcePath(
    rawPath: String,
    sourceRoot: Path,
    sourceRootRelativePath: String,
    projectRoot: Path,
): String {
    val raw = Path.of(rawPath)
    if (raw.isAbsolute) {
        return normalizedSourcePath(rawPath, projectRoot)
    }

    val sourceRelativePath = normalizedSourcePath(rawPath, sourceRoot)
    val normalizedSourceRoot = sourceRootRelativePath.normalizedSeparators().trim('/')
    return listOf(normalizedSourceRoot, sourceRelativePath)
        .filter(String::isNotEmpty)
        .joinToString(separator = "/")
}

private fun String.normalizedSeparators(): String = replace('\\', '/')

internal fun stableSelectionRank(seed: Long, identity: String): String = sha256(
    "$seed\u0000$identity".toByteArray(StandardCharsets.UTF_8)
)

internal fun <T> roundRobin(groups: List<List<T>>): List<T> {
    val maxGroupSize = groups.maxOfOrNull(List<T>::size) ?: return emptyList()
    return buildList {
        repeat(maxGroupSize) { index ->
            groups.forEach { group -> group.getOrNull(index)?.let(::add) }
        }
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun boundedError(error: Throwable): String {
    val type = error::class.qualifiedName ?: error::class.simpleName ?: "Throwable"
    val message = error.message
        ?.replace('\n', ' ')
        ?.replace(JVM_IDENTITY_SUFFIX, "@<identity>")
        ?.take(MAX_ERROR_LENGTH)
    return if (message.isNullOrBlank()) type else "$type: $message"
}

private fun combineErrors(primary: String?, additional: String?): String? = when {
    primary == null -> additional
    additional == null -> primary
    else -> "$primary; $additional".take(MAX_ERROR_LENGTH)
}

private class ProjectTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private const val MAX_ERROR_LENGTH = 1_000
private const val CENSUS_STOP_ON_COVERAGE = 0
private val CENSUS_PATH_SELECTION_STRATEGY = PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM
private val JVM_IDENTITY_SUFFIX = Regex("@[0-9a-fA-F]{6,16}")
