package org.usvm.census

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsSourceSpan
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
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
            outputDirectory.resolve(SUMMARY_FILE_NAME),
            censusJson.encodeToString(summary) + System.lineSeparator(),
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
        val normalizedCheckoutRoot = checkoutRoot.normalize().absolute()
        val projectRoot = normalizedCheckoutRoot.resolve(project.path).normalize()

        try {
            require(projectRoot.startsWith(normalizedCheckoutRoot)) {
                "Project checkout escapes the configured checkout root: $projectRoot"
            }
            require(projectRoot.isDirectory()) { "Project checkout does not exist: $projectRoot" }
            val licenseFile = projectRoot.resolve(project.licenseFile).normalize()
            require(licenseFile.startsWith(projectRoot)) { "Project license file escapes its checkout: $licenseFile" }
            require(licenseFile.exists()) {
                "Project license file does not exist: ${project.licenseFile}"
            }
            validateRevision(projectRoot, project.revision)

            val loadedFiles = loadProjectFiles(project, projectRoot)
            val files = selectFiles(project, loadedFiles)
            val scene = EtsScene(
                projectFiles = files,
                projectName = project.id,
            )
            val methods = scene.projectClasses
                .flatMap { it.methods }
                .filterNot { it.cfg.stmts.isEmpty() }
                .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                .filterNot { it.name == DEFAULT_FILE_METHOD_NAME }
                .filterNot { it.name == INSTANCE_INIT_METHOD_NAME }
                .filterNot { it.name == STATIC_INIT_METHOD_NAME }
                .sortedBy { functionId(project.id, projectRoot, it.signature, loadedFiles.pathsBySignature) }
                .take(manifest.limits.maxMethods)

            var rawEvents = 0
            var completedMethods = 0
            var timedOutMethods = 0
            var failedMethods = 0
            var projectTimedOut = false

            for (method in methods) {
                if (projectStart.elapsedNow() >= manifest.limits.projectTimeoutSeconds.seconds) {
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
                    MethodStatus.TIMEOUT -> timedOutMethods++
                    MethodStatus.TOOL_ERROR -> failedMethods++
                }
            }

            writer.write(
                buildJsonObject {
                    putCommonProjectFields(project, profile)
                    put("kind", "project_result")
                    put("status", if (projectTimedOut) "timeout" else "completed")
                    put("filesSelected", files.size)
                    put("methodsSelected", methods.size)
                    put("methodsCompleted", completedMethods)
                    put("methodsTimedOut", timedOutMethods)
                    put("methodsFailed", failedMethods)
                    put("rawEvents", rawEvents)
                    put("durationMillis", projectStart.elapsedNow().inWholeMilliseconds)
                    if (projectTimedOut) {
                        put("error", "Project timeout reached before all selected methods were analyzed")
                    }
                }
            )
        } catch (error: Exception) {
            writer.write(
                buildJsonObject {
                    putCommonProjectFields(project, profile)
                    put("kind", "project_result")
                    put("status", "tool_error")
                    put("durationMillis", projectStart.elapsedNow().inWholeMilliseconds)
                    put("error", boundedError(error))
                }
            )
        }
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
            randomSeed = 0,
            timeout = methodTimeout,
            solverType = SolverType.YICES,
        )
        val emptyModelSelection = TsUnknownCallModelSelection.Only(emptySet())
        val tsOptions = TsOptions(
            unknownCallModelSelection = emptyModelSelection,
            unknownCallFallback = profile.fallback,
        )

        val analysisStart = TimeSource.Monotonic.markNow()
        var status = MethodStatus.COMPLETED
        var errorText: String? = null

        try {
            TsMachine(
                scene = scene,
                options = machineOptions,
                tsOptions = tsOptions,
                observer = observer,
            ).use { machine ->
                machine.analyze(methods = listOf(method))
            }

            if (analysisStart.elapsedNow() >= methodTimeout) {
                status = MethodStatus.TIMEOUT
                errorText = "Machine timeout reached"
            }
        } catch (error: Exception) {
            status = MethodStatus.TOOL_ERROR
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
                errorText?.let { put("error", it) }
            }
        )

        return MethodRunResult(status = status, events = observer.eventCount)
    }

    private fun selectFiles(
        project: UnknownCallCensusProject,
        loadedFiles: LoadedProjectFiles,
    ): List<EtsFile> = loadedFiles.files.asSequence()
        .map { file -> file to requireNotNull(loadedFiles.pathsBySignature[file.signature]) }
        .filter { (_, fileName) -> project.includeSuffixes.any(fileName::endsWith) }
        .filterNot { (_, fileName) -> project.excludeSuffixes.any(fileName::endsWith) }
        .distinctBy { (_, fileName) -> fileName }
        .sortedBy { (_, fileName) -> fileName }
        .take(manifest.limits.maxFiles)
        .map { (file, _) -> file }
        .toList()

    private fun loadProjectFiles(project: UnknownCallCensusProject, projectRoot: Path): LoadedProjectFiles {
        val sourceRoots = project.include.ifEmpty { listOf("") }
        val files = mutableListOf<EtsFile>()
        val pathsBySignature = hashMapOf<EtsFileSignature, String>()

        sourceRoots.forEach { relativePath ->
            val sourceRoot = projectRoot.resolve(relativePath).normalize()
            require(sourceRoot.startsWith(projectRoot)) { "Configured source root escapes project checkout: $sourceRoot" }
            require(sourceRoot.isDirectory()) { "Configured source root does not exist: $sourceRoot" }
            val loadedFiles = loadEtsProjectAutoConvert(
                sourceRoot,
                provider = EtsIrProvider.TS_FRONTEND,
            ).projectFiles

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
        require(manifest.limits.maxFiles > 0) { "File limit must be positive" }
        require(manifest.limits.maxMethods > 0) { "Method limit must be positive" }
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
        val emptyModelSelection = TsUnknownCallModelSelection.Only(emptySet())
        val emptyCatalogFingerprint = TsBuiltInUnknownCallModels.catalog(emptyModelSelection).fingerprint

        put("kind", "run_start")
        put("schemaVersion", CENSUS_SCHEMA_VERSION)
        put("startedAt", startedAt.toString())
        put("manifestSha256", sha256(Files.readAllBytes(manifestPath)))
        put("toolRevision", gitObject(Path.of("."), ref = "HEAD") ?: "unknown")
        put("toolTree", gitObject(Path.of("."), ref = "HEAD^{tree}") ?: "unknown")
        put("jacodbArtifact", runtimeArtifactName(EtsScene::class.java))
        put("frontendProvider", EtsIrProvider.TS_FRONTEND.name)
        put("solver", SolverType.YICES.name)
        put("javaVersion", System.getProperty("java.version"))
        put("randomSeed", 0)
        put("unknownCallModelSelection", "NONE")
        put("unknownCallModelCatalogFingerprint", emptyCatalogFingerprint)
        put("legacyApproximationPolicy", "UNCHANGED")
        put("projectTimeoutSeconds", manifest.limits.projectTimeoutSeconds)
        put("methodTimeoutSeconds", manifest.limits.methodTimeoutSeconds)
        put("maxFiles", manifest.limits.maxFiles)
        put("maxMethods", manifest.limits.maxMethods)
    }

    private fun validateRevision(projectRoot: Path, expectedRevision: String) {
        val actualRevision = gitObject(projectRoot, ref = "HEAD")
            ?: error("Cannot read Git revision for project checkout $projectRoot")
        require(actualRevision.equals(expectedRevision, ignoreCase = true)) {
            "Project checkout $projectRoot is at $actualRevision, expected $expectedRevision"
        }
    }

    private fun gitObject(directory: Path, ref: String): String? = runCatching {
        val process = ProcessBuilder("git", "-C", directory.pathString, "rev-parse", ref)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.waitFor() == 0) output else null
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val RAW_FILE_NAME = "raw.jsonl"
        const val SUMMARY_FILE_NAME = "summary.json"
    }
}

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

    override fun onUnknownCall(event: TsUnknownCallEvent) {
        eventCount++
        val containingSignature = event.callSite.location.method.signature
        val functionId = functionId(project.id, projectRoot, containingSignature, pathsBySignature)
        val sourceSpan = event.callSite.location.origin
        val sourceFile = sourcePath(containingSignature, projectRoot, pathsBySignature)
        val siteId = siteId(functionId, sourceSpan, event.callSite.location.index)
        val decision = event.decision.serializedName

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
                put("eventIndex", eventCount)
            }
        )
    }
}

internal class CensusRecordWriter(output: Path) : AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(
        output,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
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

private enum class MethodStatus(val serializedName: String) {
    COMPLETED("completed"),
    TIMEOUT("timeout"),
    TOOL_ERROR("tool_error"),
}

private data class MethodRunResult(
    val status: MethodStatus,
    val events: Int,
)

private data class LoadedProjectFiles(
    val files: List<EtsFile>,
    val pathsBySignature: Map<EtsFileSignature, String>,
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
): String = pathsBySignature[signature.enclosingClass.file]
    ?: normalizedSourcePath(signature.enclosingClass.file.fileName, projectRoot)

private fun signatureKey(signature: EtsMethodSignature): String {
    val parameters = signature.parameters.joinToString(separator = ",") { parameter -> parameter.type.toString() }
    return "${signature.enclosingClass.name}:${signature.name}($parameters)->${signature.returnType}"
}

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

private fun boundedError(error: Throwable): String {
    val type = error::class.qualifiedName ?: error::class.simpleName ?: "Throwable"
    val message = error.message
        ?.replace('\n', ' ')
        ?.replace(JVM_IDENTITY_SUFFIX, "@<identity>")
        ?.take(MAX_ERROR_LENGTH)
    return if (message.isNullOrBlank()) type else "$type: $message"
}

private const val MAX_ERROR_LENGTH = 1_000
private const val DEFAULT_FILE_METHOD_NAME = "%dflt"
private val JVM_IDENTITY_SUFFIX = Regex("@[0-9a-fA-F]{6,16}")
