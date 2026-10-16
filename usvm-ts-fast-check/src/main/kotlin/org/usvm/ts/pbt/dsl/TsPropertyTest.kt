package org.usvm.ts.pbt.dsl

import kotlinx.serialization.json.Json
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureDetails
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.WitnessEvidenceDelta
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmCoverageFeedback
import org.usvm.ts.pbt.usvm.UsvmPropertySearchResult
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Selects execution without changing the author property or its JavaScript semantics. */
enum class PropertyEngine {
    FAST_CHECK,
    USVM,
    BOTH,
}

/** Keeps concrete runs, symbolic classifications, and concrete candidate validation distinct. */
data class DslPropertyRun(
    val property: CompiledDslProperty,
    val concrete: PropertyRunResult?,
    val symbolic: UsvmPropertySearchResult?,
    val symbolicReplay: WitnessEvidenceDelta?,
    val elapsedMillis: Long = 0,
    val budgetMillis: Long? = null,
    val symbolicSkippedDueToDeadline: Boolean = false,
) {
    fun requireSuccess() {
        val concreteFailureDetails = concrete?.failure
        val concreteViolation = concreteFailureDetails?.kind == PropertyFailureKind.PROPERTY
        val confirmed = symbolicReplay?.status == WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION
        val violation = when {
            concreteViolation -> {
                val run = requireNotNull(concrete)
                val replayDetails = "seed=${run.seed}, path=${run.replayPath}, shrinks=${run.numShrinks}"
                violationMessage(requireNotNull(concreteFailureDetails), run.counterexample, replayDetails)
            }
            confirmed -> {
                val evidence = requireNotNull(symbolicReplay)
                val replay = evidence.reducedReplay ?: requireNotNull(evidence.originalReplay)
                val values = evidence.reduction.inputs ?: evidence.candidate.inputs
                val replayDetails = "native confirmation, shrinks=${evidence.reduction.acceptedShrinks}"
                violationMessage(requireNotNull(replay.failure), values, replayDetails)
            }
            else -> {
                null
            }
        }

        if (violation != null) throw DslPropertyAssertionError(this, violation)
        val concreteFailure = concrete?.status == PropertyRunStatus.FAILURE
        val incompleteSearch = symbolic != null && symbolic.status != UsvmPropertySearchStatus.NO_VIOLATION_REACHED
        if (concreteFailure || incompleteSearch || symbolicSkippedDueToDeadline) {
            throw DslPropertyExecutionError(this, "Property run was inconclusive: ${concrete?.failure ?: symbolic}")
        }
    }

    private fun violationMessage(
        failure: PropertyFailureDetails,
        values: List<JsConcreteValue>?,
        replayDetails: String,
    ): String {
        val check = property.program.checks.firstOrNull { it.name == failure.checkId }
        val inputs = property.program.inputs.zip(values.orEmpty()).joinToString { (input, value) ->
            "${input.name}=${displayValue(value)}"
        }
        return buildString {
            append("Property ${property.definition.id}: check ${failure.checkId ?: "predicate"} failed")
            check?.source?.let { append(" at ${it.fileName}:${it.line}") }
            append("\n$inputs")
            append("\nactual=${displayValue(failure.actual)}, expected=${displayValue(failure.expected)}")
            append("\n${failure.errorName}: ${failure.message}")
            append("\n$replayDetails")
        }
    }
}

private fun displayValue(value: JsConcreteValue?): String = when (value) {
    null -> "unavailable"
    JsConcreteValue.Undefined -> "undefined"
    JsConcreteValue.Null -> "null"
    is JsConcreteValue.Boolean -> value.value.toString()
    is JsConcreteValue.Number -> value.toDouble().toString().removeSuffix(".0")
    is JsConcreteValue.String -> Json.encodeToUtf8SafeString(value.value)
    is JsConcreteValue.Array -> value.elements.joinToString(prefix = "[", postfix = "]", transform = ::displayValue)
    is JsConcreteValue.Object -> value.fields.entries.joinToString(prefix = "{", postfix = "}") { (key, field) ->
        "${Json.encodeToUtf8SafeString(key)}: ${displayValue(field)}"
    }
}

/** Ordinary assertion failure carrying the minimized result and original expression tree. */
class DslPropertyAssertionError(val result: DslPropertyRun, message: String) : AssertionError(message)

/** Incomplete execution is distinct from a demonstrated author-check violation. */
class DslPropertyExecutionError(val result: DslPropertyRun, message: String) : IllegalStateException(message)

/** Executes a reusable Kotlin property through the existing concrete and symbolic interfaces. */
class DslPropertyRunner(sourceRoots: List<Path>) {
    private val roots = sourceRoots.map { it.toRealPath() }

    init {
        require(roots.isNotEmpty() && roots.all(Files::isDirectory)) { "Existing TypeScript source roots are required" }
    }

    fun run(
        program: DslProperty,
        engine: PropertyEngine = PropertyEngine.FAST_CHECK,
        configuration: PropertyRunConfiguration = PropertyRunConfiguration(),
    ): DslPropertyRun {
        val started = TimeSource.Monotonic.markNow()
        val deadline = WitnessReplayDeadline(timeoutMillis = configuration.timeoutMillis)
        fun remainingMillis(): Long = deadline.remainingMillis()

        val directory = Files.createTempDirectory("usvm-kotlin-property-")
        try {
            val compiled = PropertyTypeScriptCompiler().compile(
                program = program,
                generatedModule = GENERATED_MODULE,
                resolveModule = { module -> resolveModule(module, directory) },
            )
            val generated = directory.resolve(GENERATED_MODULE)
            Files.write(generated, compiled.typeScript.toByteArray(Charsets.UTF_8))
            copySources(directory)
            val sourceRoots = listOf(directory)
            val sources = captureSources(directory)
            val backend = FastCheckBackend(sourceRoots = sourceRoots)
            val hybridConfiguration = if (engine == PropertyEngine.BOTH) {
                val request = configuration.coverageRequest ?: PropertyCoverageRequest()
                configuration.copy(
                    coverageRequest = request.copy(admittedOnly = true),
                )
            } else {
                configuration
            }
            val concrete = if (engine != PropertyEngine.USVM) {
                val concreteBudget = if (engine == PropertyEngine.BOTH) {
                    minOf(remainingMillis(), maxOf(1L, configuration.timeoutMillis / CONCRETE_BUDGET_DIVISOR))
                } else {
                    remainingMillis()
                }
                if (concreteBudget <= 0L) {
                    exhaustedConcreteRun(compiled, configuration, started.elapsedNow().inWholeMilliseconds)
                } else {
                    backend.run(
                        property = compiled.definition,
                        configuration = hybridConfiguration.copy(timeoutMillis = concreteBudget),
                    )
                }
            } else {
                null
            }
            val concreteViolation = concrete?.failure?.kind == PropertyFailureKind.PROPERTY
            val shouldSearch = engine != PropertyEngine.FAST_CHECK && !concreteViolation && remainingMillis() > 0L
            val symbolic = if (shouldSearch) {
                search(
                    compiled = compiled,
                    directory = directory,
                    sourceRoots = sourceRoots,
                    configuration = hybridConfiguration,
                    concrete = concrete,
                    backend = backend,
                    remainingMillis = ::remainingMillis,
                )
            } else {
                null
            }
            val replay = if (symbolic?.inputs != null && remainingMillis() > 0L) {
                backend.confirmOriginalWitness(
                    property = compiled.definition,
                    candidate = symbolic.captureWitness(property = compiled.definition, sources = sources),
                    configuration = WitnessReplayConfiguration(
                        timeoutMillis = configuration.timeoutMillis,
                        validationReserveMillis = VALIDATION_RESERVE_MILLIS,
                        coverageRequest = configuration.coverageRequest,
                    ),
                    deadline = deadline,
                )
            } else {
                null
            }
            return DslPropertyRun(
                property = compiled,
                concrete = concrete,
                symbolic = symbolic,
                symbolicReplay = replay,
                elapsedMillis = started.elapsedNow().inWholeMilliseconds,
                budgetMillis = configuration.timeoutMillis,
                symbolicSkippedDueToDeadline = engine != PropertyEngine.FAST_CHECK &&
                    !concreteViolation && symbolic == null,
            )
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun search(
        compiled: CompiledDslProperty,
        directory: Path,
        sourceRoots: List<Path>,
        configuration: PropertyRunConfiguration,
        concrete: PropertyRunResult?,
        backend: FastCheckBackend,
        remainingMillis: () -> Long,
    ): UsvmPropertySearchResult? {
        if (remainingMillis() <= 0L) return null

        val ir = generateEtsIR(
            keepPartialOutputOnFailure = false,
            projectPath = directory,
            isProject = true,
            loadEntrypoints = false,
            useArkAnalyzerTypeInference = 1,
            timeout = remainingMillis().coerceAtLeast(1).milliseconds,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = try {
            loadEtsProjectFromIR(projectFilesPath = ir, sdkFilesPath = null)
        } finally {
            ir.toFile().deleteRecursively()
        }
        val manifest = compiled.definition.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = sourceRoots).map(manifest, concrete?.coverage)
        val remainingBudgetMillis = remainingMillis()
        if (remainingBudgetMillis <= 0L) return null

        val finalReplayReserveMillis = remainingBudgetMillis / FINAL_REPLAY_BUDGET_DIVISOR
        val searchBudgetMillis = remainingBudgetMillis - finalReplayReserveMillis
        fun symbolicRemainingMillis(): Long = remainingMillis() - finalReplayReserveMillis

        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
            solverType = SolverType.YICES,
            timeout = searchBudgetMillis.milliseconds,
        )
        val replayBudgetMillis = searchBudgetMillis / FEEDBACK_REPLAY_BUDGET_DIVISOR
        var spentReplayMillis = 0L
        val feedback = if (concrete != null) {
            UsvmCoverageFeedback(initialCoverage = concrete.coverage) { inputs ->
                val replayRemaining = minOf(symbolicRemainingMillis(), replayBudgetMillis - spentReplayMillis)
                if (replayRemaining <= 0L) {
                    null
                } else {
                    val replayStarted = TimeSource.Monotonic.markNow()
                    try {
                        backend.run(
                            property = compiled.definition,
                            configuration = configuration.copy(
                                replayPath = null,
                                examples = listOf(inputs),
                                numRuns = 1,
                                timeoutMillis = replayRemaining,
                                exactReplay = true,
                            ),
                        )
                    } finally {
                        spentReplayMillis += replayStarted.elapsedNow().inWholeMilliseconds
                    }
                }
            }
        } else {
            null
        }
        return UsvmPropertySearcher(
            scene = scene,
            machineOptions = options,
            deadlineReached = { symbolicRemainingMillis() <= 0L },
        ).search(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            deadlineReached = { symbolicRemainingMillis() <= 0L },
            coverageFeedback = feedback,
        )
    }

    private fun exhaustedConcreteRun(
        compiled: CompiledDslProperty,
        configuration: PropertyRunConfiguration,
        elapsedMillis: Long,
    ) = PropertyRunResult(
        propertyId = compiled.definition.id,
        status = PropertyRunStatus.FAILURE,
        seed = configuration.seed ?: 0,
        replayPath = null,
        counterexample = null,
        numRuns = 0,
        numSkips = 0,
        numShrinks = 0,
        failure = PropertyFailureDetails(
            kind = PropertyFailureKind.TIMEOUT,
            errorName = "HybridDeadlineExceeded",
            message = "The total property deadline expired before the concrete run",
        ),
        executionTimeMillis = elapsedMillis,
    )

    private fun captureSources(directory: Path): List<PropertyObservationSource> = Files.walk(directory).use { paths ->
        paths.filter(Files::isRegularFile).iterator().asSequence().map { source ->
            val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
            val module = directory.relativize(source).toString().replace('\\', '/')

            PropertyObservationSource(module = module, sha256 = hash)
        }.toList()
    }

    private fun resolveModule(module: String, directory: Path): String {
        val modulePath = Path.of(module)
        val normalized = !modulePath.isAbsolute && '\\' !in module &&
            module.split('/').none { it.isEmpty() || it == "." || it == ".." }
        require(normalized) { "Expected a project-relative TypeScript module: $module" }
        val matches = roots.mapIndexedNotNull { index, root ->
            val candidate = root.resolve(modulePath)
            if (!Files.isRegularFile(candidate)) return@mapIndexedNotNull null
            val real = candidate.toRealPath()
            require(real.startsWith(root)) { "TypeScript module escapes its source root: $module" }
            index to real
        }.distinct()
        require(matches.size == 1) { "Expected one TypeScript module for $module, found ${matches.size}" }
        val (index, original) = matches.single()
        val relative = roots[index].relativize(original)
        val target = directory.resolve("source$index").resolve(relative)
        return "./" + directory.relativize(target).toString().replace('\\', '/')
    }

    private fun copySources(directory: Path) {
        roots.forEachIndexed { index, root ->
            Files.walk(root).use { paths ->
                paths.filter(Files::isRegularFile).forEach { source ->
                    val relative = root.relativize(source)
                    val excluded = relative.any { it.toString() in EXCLUDED_DIRECTORIES }
                    val supported = source.toString().endsWith(".ts") || source.toString().endsWith(".ets")
                    if (!excluded && supported) {
                        val original = source.toRealPath()
                        require(original.startsWith(root)) { "TypeScript source escapes its source root: $source" }
                        val target = directory.resolve("source$index").resolve(relative)
                        Files.createDirectories(target.parent)
                        Files.copy(source, target)
                    }
                }
            }
        }
    }

    private companion object {
        const val GENERATED_MODULE = "__usvm_kotlin_property.ts"
        const val VALIDATION_RESERVE_MILLIS = 5_000L
        const val BYTE_MASK = 0xff
        const val CONCRETE_BUDGET_DIVISOR = 3L
        const val FEEDBACK_REPLAY_BUDGET_DIVISOR = 3L
        const val FINAL_REPLAY_BUDGET_DIVISOR = 3L
        val EXCLUDED_DIRECTORIES = setOf("node_modules", ".git", ".gradle", "build")
    }
}

/** Base for ordinary JUnit tests; suite configuration applies to every property unless overridden. */
open class TsPropertyTest(
    sourceRoot: String,
    private val engine: PropertyEngine = PropertyEngine.FAST_CHECK,
    private val configuration: PropertyRunConfiguration = PropertyRunConfiguration(),
) {
    private val runner by lazy { DslPropertyRunner(sourceRoots = listOf(Path.of(sourceRoot))) }

    protected fun property(
        id: String? = null,
        engine: PropertyEngine = this.engine,
        configuration: PropertyRunConfiguration = this.configuration,
        declaration: PropertyBuilder.() -> Unit,
    ) {
        val caller = Thread.currentThread().stackTrace.first { frame ->
            frame.className != Thread::class.java.name && frame.className != TsPropertyTest::class.java.name
        }
        val inferredId = "${caller.className}.${caller.methodName}".replace(Regex("[^A-Za-z0-9._/-]"), "_")
        val program = defineProperty(id = id ?: inferredId, declaration = declaration)
        runner.run(program = program, engine = engine, configuration = configuration).requireSuccess()
    }
}
