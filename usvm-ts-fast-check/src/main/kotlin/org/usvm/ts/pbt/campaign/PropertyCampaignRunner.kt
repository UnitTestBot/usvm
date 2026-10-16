package org.usvm.ts.pbt.campaign

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrGenerationTimeoutException
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.WitnessEvidenceDelta
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.fastcheck.BackendErrorKind
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmCoverageFeedback
import org.usvm.ts.pbt.usvm.UsvmPropertySearchResult
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import org.usvm.ts.pbt.validation.requireValid
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@Serializable
enum class PropertyCampaignMode { PBT_ONLY, SYMBOLIC_ONLY, SEQUENTIAL, COVERAGE_FEEDBACK }

@Serializable
enum class PropertyCampaignPhase { PREPARATION, PBT, SYMBOLIC, CONFIRMATION }

/** COMPLETED describes orchestration; inspect the retained engine results for unsupported or incomplete work. */
@Serializable
enum class PropertyCampaignStop { COMPLETED, DEADLINE, EXECUTION_FAILURE }

/** Only native violations count as confirmed; symbolic inputs remain candidates until replay succeeds. */
@Serializable
data class PropertyCampaignResult(
    val propertyId: PropertyId,
    val mode: PropertyCampaignMode,
    val concrete: PropertyRunResult?,
    val symbolic: UsvmPropertySearchResult?,
    val confirmation: WitnessEvidenceDelta?,
    val stop: PropertyCampaignStop,
    val stoppedAt: PropertyCampaignPhase?,
    val elapsedMillis: Long,
    val diagnostic: String?,
) {
    val confirmedViolation: Boolean
        get() = concrete?.failure?.kind == PropertyFailureKind.PROPERTY ||
            confirmation?.status == WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION
}

/** One concrete generation phase followed by the unchanged original-property symbolic query. */
class PropertyCampaignRunner(
    private val sourceRoot: Path,
    private val tsconfig: Path? = null,
    private val nodeExecutable: String = "node",
    private val machineOptions: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
) {
    /** Captures the requested direct controls and their actual result without repeating execution. */
    fun runArtifact(
        property: PropertyDefinition,
        mode: PropertyCampaignMode,
        configuration: PropertyRunConfiguration = PropertyRunConfiguration(),
        validationReserveMillis: Long = DEFAULT_VALIDATION_RESERVE_MILLIS,
        pbtAllocationMillis: Long? = null,
        shrinkWitness: Boolean = true,
    ): PropertyCampaignArtifact {
        validateControls(mode, configuration, validationReserveMillis, pbtAllocationMillis)
        val guided = mode == PropertyCampaignMode.COVERAGE_FEEDBACK
        val startedAt = System.nanoTime()
        val deadline = WitnessReplayDeadline(timeoutMillis = configuration.timeoutMillis)
        requireValid(validatePropertyDefinition(property))

        var usedConfiguration = configuration
        var phaseDiagnostic: String? = null
        var metadata: PropertyCampaignMetadata? = null
        var phase = PropertyCampaignPhase.PREPARATION
        var concrete: PropertyRunResult? = null
        var symbolic: UsvmPropertySearchResult? = null
        var confirmation: WitnessEvidenceDelta? = null

        fun result(
            stop: PropertyCampaignStop = PropertyCampaignStop.COMPLETED,
            diagnostic: String? = phaseDiagnostic,
        ) = PropertyCampaignArtifact(
            property = property,
            configuration = usedConfiguration,
            validationReserveMillis = validationReserveMillis,
            pbtAllocationMillis = pbtAllocationMillis,
            shrinkWitness = shrinkWitness,
            result = PropertyCampaignResult(
                propertyId = property.id,
                mode = mode,
                concrete = concrete,
                symbolic = symbolic,
                confirmation = confirmation,
                stop = stop,
                stoppedAt = phase.takeUnless { stop == PropertyCampaignStop.COMPLETED },
                elapsedMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                diagnostic = listOfNotNull(phaseDiagnostic, diagnostic).distinct()
                    .joinToString(separator = "; ").takeIf(String::isNotEmpty),
            ),
            metadata = metadata,
        )

        try {
            val root = sourceRoot.toRealPath()
            if (deadline.remainingMillis() <= 0) return result(PropertyCampaignStop.DEADLINE)

            metadata = captureMetadata(property = property, root = root)
            if (deadline.remainingMillis() <= 0) return result(PropertyCampaignStop.DEADLINE)

            val backend = FastCheckBackend(listOf(root), nodeExecutable = nodeExecutable, tsconfig = tsconfig)
            if (deadline.remainingMillis() <= 0) return result(PropertyCampaignStop.DEADLINE)

            usedConfiguration = evidenceConfiguration(configuration, requireNotNull(metadata).sources, guided)

            if (mode != PropertyCampaignMode.SYMBOLIC_ONLY) {
                phase = PropertyCampaignPhase.PBT
                val generation = generate(property, backend, usedConfiguration, deadline, pbtAllocationMillis)
                concrete = generation.getOrNull()
                phaseDiagnostic = generation.exceptionOrNull()?.let { error ->
                    "PBT allocation expired: ${error.message}"
                }

                if (deadline.remainingMillis() <= 0) return result(PropertyCampaignStop.DEADLINE)
                if (concrete?.failure?.kind == PropertyFailureKind.PROPERTY) return result()
                val coverageTimedOut = concrete?.let(::coverageCollectionTimedOut) == true
                val generationTimedOut = concrete?.failure?.kind == PropertyFailureKind.TIMEOUT
                if ((coverageTimedOut || generationTimedOut) && pbtAllocationMillis == null) {
                    return result(PropertyCampaignStop.DEADLINE)
                }
                if (coverageTimedOut || generationTimedOut) {
                    phaseDiagnostic = "PBT allocation ended with incomplete evidence; " +
                        "symbolic search uses the remainder"
                }
                if (mode == PropertyCampaignMode.PBT_ONLY) return result()
            }

            phase = PropertyCampaignPhase.SYMBOLIC
            if (deadline.remainingMillis() <= validationReserveMillis) {
                return result(PropertyCampaignStop.DEADLINE)
            }

            val sources = captureSources(property, root)
            symbolic = search(
                property = property,
                root = root,
                deadline = deadline,
                validationReserveMillis = validationReserveMillis,
                feedback = if (guided) {
                    coverageFeedback(
                        property, concrete, backend, sources, usedConfiguration, deadline, validationReserveMillis,
                    )
                } else {
                    null
                },
            )
            if (symbolic == null) return result(PropertyCampaignStop.DEADLINE)

            val searchTimedOut = symbolic.status == UsvmPropertySearchStatus.TIMEOUT
            if (symbolic.target == null) {
                val stop = if (searchTimedOut) PropertyCampaignStop.DEADLINE else PropertyCampaignStop.COMPLETED
                return result(stop)
            }

            phase = PropertyCampaignPhase.CONFIRMATION
            confirmation = confirm(
                backend, property, symbolic, sources, usedConfiguration, deadline,
                validationReserveMillis, shrinkWitness,
            )

            return if (confirmation.status == WitnessReplayStatus.TIMEOUT) {
                result(PropertyCampaignStop.DEADLINE)
            } else {
                result()
            }
        } catch (error: PbtBackendException) {
            val stop = if (error.kind == BackendErrorKind.TIMEOUT) {
                PropertyCampaignStop.DEADLINE
            } else {
                PropertyCampaignStop.EXECUTION_FAILURE
            }
            return result(stop = stop, diagnostic = "${error.code}: ${error.message}")
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: EtsIrGenerationTimeoutException) {
            return result(stop = PropertyCampaignStop.DEADLINE, diagnostic = error.message)
        } catch (error: IOException) {
            return result(stop = PropertyCampaignStop.EXECUTION_FAILURE, diagnostic = error.message)
        } catch (error: IllegalArgumentException) {
            return result(stop = PropertyCampaignStop.EXECUTION_FAILURE, diagnostic = error.message)
        } catch (error: IllegalStateException) {
            return result(
                stop = PropertyCampaignStop.EXECUTION_FAILURE,
                diagnostic = "${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }

    fun run(
        property: PropertyDefinition,
        mode: PropertyCampaignMode,
        configuration: PropertyRunConfiguration = PropertyRunConfiguration(),
        validationReserveMillis: Long = DEFAULT_VALIDATION_RESERVE_MILLIS,
        pbtAllocationMillis: Long? = null,
        shrinkWitness: Boolean = true,
    ): PropertyCampaignResult = runArtifact(
        property = property,
        mode = mode,
        configuration = configuration,
        validationReserveMillis = validationReserveMillis,
        pbtAllocationMillis = pbtAllocationMillis,
        shrinkWitness = shrinkWitness,
    ).result

    private fun validateControls(
        mode: PropertyCampaignMode,
        configuration: PropertyRunConfiguration,
        validationReserveMillis: Long,
        pbtAllocationMillis: Long?,
    ) {
        require(validationReserveMillis > 0) { "Validation reserve must be positive" }
        val guided = mode == PropertyCampaignMode.COVERAGE_FEEDBACK
        require(!guided || pbtAllocationMillis != null) { "Coverage feedback requires an explicit PBT allocation" }
        require(pbtAllocationMillis == null || pbtAllocationMillis in 1 until configuration.timeoutMillis) {
            "PBT allocation must be positive and smaller than the total campaign timeout"
        }
        require(pbtAllocationMillis == null || mode == PropertyCampaignMode.SEQUENTIAL || guided) {
            "PBT allocation requires a two-phase campaign"
        }
    }

    private fun evidenceConfiguration(
        configuration: PropertyRunConfiguration,
        sources: List<PropertyObservationSource>,
        guided: Boolean,
    ): PropertyRunConfiguration {
        if (!guided) return configuration

        val request = configuration.coverageRequest ?: PropertyCoverageRequest()
        return configuration.copy(
            coverageRequest = request.copy(admittedOnly = true),
            observationRequest = configuration.observationRequest ?: PropertyObservationRequest(
                points = emptyList(),
                sources = sources,
            ),
        )
    }

    private fun generate(
        property: PropertyDefinition,
        backend: FastCheckBackend,
        configuration: PropertyRunConfiguration,
        deadline: WitnessReplayDeadline,
        pbtAllocationMillis: Long?,
    ): Result<PropertyRunResult> {
        val allocation = pbtAllocationMillis?.coerceAtMost(deadline.remainingMillis())
        val generationDeadline = allocation?.let { WitnessReplayDeadline(timeoutMillis = it.coerceAtLeast(1)) }
            ?: deadline
        val generationConfiguration = configuration.copy(
            timeoutMillis = allocation?.coerceAtLeast(1) ?: configuration.timeoutMillis,
        )

        return try {
            Result.success(backend.run(property, generationConfiguration, generationDeadline))
        } catch (error: PbtBackendException) {
            val phaseExpired = allocation != null && error.kind == BackendErrorKind.TIMEOUT
            if (!phaseExpired) throw error

            Result.failure(error)
        }
    }

    private fun captureMetadata(property: PropertyDefinition, root: Path): PropertyCampaignMetadata {
        val explicitTsconfig = tsconfig?.toRealPath()
        val configuration = explicitTsconfig?.let { path ->
            PropertyCampaignTsconfig(path = path.toString(), sha256 = campaignSourceSha256(path))
        }

        return PropertyCampaignMetadata(
            sourceRoot = root.toString(),
            requestedNodeCommand = nodeExecutable,
            sources = captureSources(property = property, root = root),
            explicitConcreteTsconfig = configuration,
        )
    }

    private fun coverageCollectionTimedOut(result: PropertyRunResult): Boolean =
        result.coverage?.diagnostics?.any { diagnostic ->
            diagnostic.code == FastCheckDiagnosticCode.COVERAGE_COLLECTION_TIMEOUT
        } == true

    private fun loadScene(root: Path, timeoutMillis: Long): EtsScene {
        val directory = generateEtsIR(
            keepPartialOutputOnFailure = false,
            projectPath = root,
            isProject = true,
            loadEntrypoints = false,
            useArkAnalyzerTypeInference = 1,
            timeout = timeoutMillis.milliseconds,
            provider = EtsIrProvider.TS_FRONTEND,
        )

        try {
            return loadEtsProjectFromIR(projectFilesPath = directory, sdkFilesPath = null)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun search(
        property: PropertyDefinition,
        root: Path,
        deadline: WitnessReplayDeadline,
        validationReserveMillis: Long,
        feedback: UsvmCoverageFeedback?,
    ): UsvmPropertySearchResult? {
        if (deadline.remainingMillis() <= validationReserveMillis) return null

        val scene = loadScene(root, timeoutMillis = deadline.remainingMillis() - validationReserveMillis)
        if (deadline.remainingMillis() <= validationReserveMillis) return null

        val manifest = property.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = listOf(root)).map(manifest)
        val budgetMillis = deadline.remainingMillis() - validationReserveMillis
        if (budgetMillis <= 0) return null

        val searchTimeout = minOf(machineOptions.timeout, budgetMillis.milliseconds)
        return UsvmPropertySearcher(
            scene = scene,
            machineOptions = machineOptions.copy(timeout = searchTimeout, stopOnCoverage = 0),
        ).search(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            deadlineReached = { deadline.remainingMillis() <= validationReserveMillis },
            coverageFeedback = feedback,
        )
    }

    private fun confirm(
        backend: FastCheckBackend,
        property: PropertyDefinition,
        symbolic: UsvmPropertySearchResult,
        sources: List<PropertyObservationSource>,
        configuration: PropertyRunConfiguration,
        deadline: WitnessReplayDeadline,
        validationReserveMillis: Long,
        shrinkWitness: Boolean,
    ): WitnessEvidenceDelta {
        val candidate = symbolic.captureWitness(property = property, sources = sources)
        val replayConfiguration = WitnessReplayConfiguration(
            timeoutMillis = configuration.timeoutMillis,
            validationReserveMillis = validationReserveMillis,
            coverageRequest = configuration.coverageRequest,
            shrink = shrinkWitness,
        )
        return if (property.assertions.isEmpty()) {
            backend.confirmWitness(property, candidate, replayConfiguration, deadline)
        } else {
            backend.confirmOriginalWitness(property, candidate, replayConfiguration, deadline)
        }
    }

    private fun coverageFeedback(
        property: PropertyDefinition,
        concrete: PropertyRunResult?,
        backend: FastCheckBackend,
        sources: List<PropertyObservationSource>,
        configuration: PropertyRunConfiguration,
        deadline: WitnessReplayDeadline,
        validationReserveMillis: Long,
    ) = UsvmCoverageFeedback(
        initialCoverage = concrete?.coverage,
        nativeReplay = { inputs ->
            val remainingMillis = deadline.remainingMillis() - validationReserveMillis
            if (remainingMillis <= 0) {
                null
            } else {
                val candidate = property.captureWitness(
                    inputs = inputs,
                    target = WitnessTarget(id = property.predicate.exportName),
                    sources = sources,
                    provenance = "usvm:coverage",
                )
                backend.replayInput(
                    property = property,
                    candidate = candidate,
                    coverageRequest = requireNotNull(configuration.coverageRequest),
                    deadline = WitnessReplayDeadline(timeoutMillis = remainingMillis),
                )
            }
        },
    )

    private fun captureSources(property: PropertyDefinition, root: Path): List<PropertyObservationSource> {
        val modules = listOfNotNull(property.predicate.module, property.precondition?.module).distinct()

        return modules.map { module ->
            val path = root.resolve(module).toRealPath()
            require(path.startsWith(root)) { "Property module escapes its source root: $module" }

            PropertyObservationSource(module = module, sha256 = campaignSourceSha256(path))
        }
    }

    companion object {
        const val DEFAULT_VALIDATION_RESERVE_MILLIS = 5_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
