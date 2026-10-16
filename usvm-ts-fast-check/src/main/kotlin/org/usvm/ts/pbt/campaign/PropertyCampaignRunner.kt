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
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.fastcheck.BackendErrorKind
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
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
    val symbolicInputReplays: List<SymbolicInputReplay> = emptyList(),
    val relationGuidance: PropertyRelationGuidanceTrace? = null,
) {
    val confirmedViolation: Boolean
        get() = concrete?.failure?.kind == PropertyFailureKind.PROPERTY ||
            confirmation?.status == WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION ||
            relationGuidance?.attempts?.any {
                it.confirmation?.status == WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION
            } == true
}

/** One concrete generation phase followed by the unchanged original-property symbolic query. */
class PropertyCampaignRunner(
    private val sourceRoot: Path,
    private val tsconfig: Path? = null,
    private val nodeExecutable: String = "node",
    private val machineOptions: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
) {
    /** Keeps phase transitions and retained partial results under the same deadline. */
    @Suppress("LongMethod")
    fun runArtifact(
        property: PropertyDefinition,
        mode: PropertyCampaignMode,
        configuration: PropertyRunConfiguration = PropertyRunConfiguration(),
        validationReserveMillis: Long = DEFAULT_VALIDATION_RESERVE_MILLIS,
        pbtAllocationMillis: Long? = null,
        shrinkWitness: Boolean = true,
        completedInputLimit: Int = 0,
        relationGuidance: PropertyRelationGuidance? = null,
    ): PropertyCampaignArtifact {
        require(completedInputLimit >= 0) { "Completed input limit must not be negative" }
        require(completedInputLimit == 0 || mode != PropertyCampaignMode.PBT_ONLY) {
            "Completed input capture requires a symbolic campaign mode"
        }
        validateControls(mode, configuration, validationReserveMillis, pbtAllocationMillis)
        if (relationGuidance != null) {
            require(mode == PropertyCampaignMode.SEQUENTIAL || mode == PropertyCampaignMode.COVERAGE_FEEDBACK) {
                "Relation guidance requires one PBT phase followed by a symbolic phase"
            }
            require(relationGuidance.compiled == null || relationGuidance.compiled.definition == property) {
                "Retained compiler output belongs to another property"
            }
        }
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
        var inputReplays: SymbolicInputReplayCollector? = null
        var relationTrace: PropertyRelationGuidanceTrace? = null

        fun result(
            stop: PropertyCampaignStop = PropertyCampaignStop.COMPLETED,
            diagnostic: String? = phaseDiagnostic,
        ) = PropertyCampaignArtifact(
            property = property,
            configuration = usedConfiguration,
            validationReserveMillis = validationReserveMillis,
            pbtAllocationMillis = pbtAllocationMillis,
            shrinkWitness = shrinkWitness,
            completedInputLimit = completedInputLimit,
            relationGuidanceControls = relationGuidance?.controls(),
            result = PropertyCampaignResult(
                propertyId = property.id,
                mode = mode,
                concrete = concrete,
                symbolic = symbolic,
                confirmation = confirmation,
                symbolicInputReplays = inputReplays?.replays.orEmpty(),
                relationGuidance = relationTrace,
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

            usedConfiguration = evidenceConfiguration(
                configuration, requireNotNull(metadata).sources, guided, captureNative = completedInputLimit > 0,
                relationGuidance = relationGuidance,
            )

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
            val coverageRequest = usedConfiguration.coverageRequest ?: PropertyCoverageRequest()
            if (guided || completedInputLimit > 0) {
                inputReplays = SymbolicInputReplayCollector(
                    property, backend, root, sources, coverageRequest.copy(admittedOnly = true),
                    deadline, validationReserveMillis, limit = completedInputLimit,
                )
            }
            symbolic = search(
                property = property,
                root = root,
                deadline = deadline,
                validationReserveMillis = validationReserveMillis,
                feedback = if (guided) {
                    UsvmCoverageFeedback(
                        initialCoverage = concrete?.coverage,
                        nativeReplay = requireNotNull(inputReplays)::replayForGuidance,
                    )
                } else {
                    null
                },
                completedInputLimit = completedInputLimit,
                relationChallenger = relationGuidance?.let { guidance ->
                    {
                            scene, mapping ->
                        PropertyRelationChallenger(
                            scene, mapping, backend, machineOptions, deadline, validationReserveMillis,
                        ).challenge(property, concrete?.observations, guidance, shrinkWitness)
                    }
                },
                retainRelationTrace = { relationTrace = it },
            )
            val relationFault = relationTrace?.confirmedOriginalFault()
            if (relationFault != null) {
                confirmation = relationFault
                return result()
            }
            if (symbolic == null) return result(PropertyCampaignStop.DEADLINE)

            val hasNativeWork = symbolic.target != null || symbolic.completedInputs.isNotEmpty()
            if (hasNativeWork) phase = PropertyCampaignPhase.CONFIRMATION
            if (symbolic.target != null) {
                confirmation = confirm(
                    backend, property, symbolic, inputReplays?.sources ?: sources, usedConfiguration, deadline,
                    validationReserveMillis, shrinkWitness,
                )
            }
            inputReplays?.collect(symbolic.completedInputs)
            phaseDiagnostic = listOfNotNull(phaseDiagnostic, inputReplays?.diagnostic)
                .joinToString(separator = "; ").takeIf(String::isNotEmpty)

            val expired = symbolic.status == UsvmPropertySearchStatus.TIMEOUT ||
                confirmation?.status == WitnessReplayStatus.TIMEOUT || inputReplays?.timedOut == true ||
                deadline.remainingMillis() <= 0
            val stop = if (expired) PropertyCampaignStop.DEADLINE else PropertyCampaignStop.COMPLETED
            return result(stop)
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
        completedInputLimit: Int = 0,
        relationGuidance: PropertyRelationGuidance? = null,
    ): PropertyCampaignResult = runArtifact(
        property = property,
        mode = mode,
        configuration = configuration,
        validationReserveMillis = validationReserveMillis,
        pbtAllocationMillis = pbtAllocationMillis,
        shrinkWitness = shrinkWitness,
        completedInputLimit = completedInputLimit,
        relationGuidance = relationGuidance,
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
        captureNative: Boolean,
        relationGuidance: PropertyRelationGuidance?,
    ): PropertyRunConfiguration {
        if (!guided && !captureNative && relationGuidance == null) return configuration

        val request = configuration.coverageRequest ?: PropertyCoverageRequest()
        return configuration.copy(
            coverageRequest = request.copy(admittedOnly = true),
            observationRequest = if (relationGuidance != null) {
                configuration.observationRequest ?: PropertyObservationRequest(
                    points = relationGuidance.pairs.flatMap { listOf(it.left, it.right) }.distinct(),
                    sources = sources,
                )
            } else if (guided && configuration.inputExportRequest == null) {
                configuration.observationRequest ?: PropertyObservationRequest(points = emptyList(), sources = sources)
            } else {
                configuration.observationRequest
            },
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
        completedInputLimit: Int,
        relationChallenger: ((EtsScene, PropertyEtsMappingArtifact) -> PropertyRelationGuidanceTrace)?,
        retainRelationTrace: (PropertyRelationGuidanceTrace) -> Unit,
    ): UsvmPropertySearchResult? {
        if (deadline.remainingMillis() <= validationReserveMillis) return null

        val scene = loadScene(root, timeoutMillis = deadline.remainingMillis() - validationReserveMillis)
        if (deadline.remainingMillis() <= validationReserveMillis) return null

        val manifest = property.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = listOf(root)).map(manifest)
        val relationTrace = relationChallenger?.invoke(scene, mapping)
        if (relationTrace?.confirmedOriginalFault() != null) {
            retainRelationTrace(relationTrace)
            return null
        }
        val budgetMillis = deadline.remainingMillis() - validationReserveMillis
        relationTrace?.let {
            retainRelationTrace(it.copy(originalAllocatedMillis = budgetMillis.coerceAtLeast(minimumValue = 0)))
        }
        if (budgetMillis <= 0) return null

        val searchTimeout = minOf(machineOptions.timeout, budgetMillis.milliseconds)
        val originalStartedAt = System.nanoTime()
        val result = UsvmPropertySearcher(
            scene = scene,
            machineOptions = machineOptions.copy(timeout = searchTimeout, stopOnCoverage = 0),
        ).search(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            deadlineReached = { deadline.remainingMillis() <= validationReserveMillis },
            coverageFeedback = feedback,
            completedInputLimit = completedInputLimit,
        )
        relationTrace?.let {
            retainRelationTrace(
                it.copy(
                    originalAllocatedMillis = searchTimeout.inWholeMilliseconds,
                    originalElapsedMillis = (System.nanoTime() - originalStartedAt) / NANOS_PER_MILLISECOND,
                )
            )
        }
        return result
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
