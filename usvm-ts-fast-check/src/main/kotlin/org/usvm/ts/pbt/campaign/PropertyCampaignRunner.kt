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
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmPropertySearchResult
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import org.usvm.ts.pbt.validation.requireValid
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@Serializable
enum class PropertyCampaignMode { PBT_ONLY, SYMBOLIC_ONLY, SEQUENTIAL }

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

/** Direct controls over the same original property; coverage and observations are retained without feedback. */
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
    ): PropertyCampaignArtifact {
        val result = run(property, mode, configuration, validationReserveMillis)

        return PropertyCampaignArtifact(property, configuration, validationReserveMillis, result)
    }

    fun run(
        property: PropertyDefinition,
        mode: PropertyCampaignMode,
        configuration: PropertyRunConfiguration = PropertyRunConfiguration(),
        validationReserveMillis: Long = DEFAULT_VALIDATION_RESERVE_MILLIS,
    ): PropertyCampaignResult {
        require(validationReserveMillis > 0) { "Validation reserve must be positive" }
        val startedAt = System.nanoTime()
        val deadline = WitnessReplayDeadline(timeoutMillis = configuration.timeoutMillis)
        requireValid(validatePropertyDefinition(property))

        var phase = PropertyCampaignPhase.PREPARATION
        var concrete: PropertyRunResult? = null
        var symbolic: UsvmPropertySearchResult? = null
        var confirmation: WitnessEvidenceDelta? = null

        fun result(
            stop: PropertyCampaignStop = PropertyCampaignStop.COMPLETED,
            diagnostic: String? = null,
        ) = PropertyCampaignResult(
            propertyId = property.id,
            mode = mode,
            concrete = concrete,
            symbolic = symbolic,
            confirmation = confirmation,
            stop = stop,
            stoppedAt = phase.takeUnless { stop == PropertyCampaignStop.COMPLETED },
            elapsedMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
            diagnostic = diagnostic,
        )

        try {
            val root = sourceRoot.toRealPath()
            val roots = listOf(root)
            val backend = FastCheckBackend(sourceRoots = roots, nodeExecutable = nodeExecutable, tsconfig = tsconfig)
            if (deadline.remainingMillis() <= 0) return result(PropertyCampaignStop.DEADLINE)

            if (mode != PropertyCampaignMode.SYMBOLIC_ONLY) {
                phase = PropertyCampaignPhase.PBT
                concrete = backend.run(property = property, configuration = configuration, deadline = deadline)

                val coverageTimedOut = concrete.coverage?.diagnostics?.any { diagnostic ->
                    diagnostic.code == FastCheckDiagnosticCode.COVERAGE_COLLECTION_TIMEOUT
                } == true
                if (coverageTimedOut || deadline.remainingMillis() <= 0) {
                    return result(
                        stop = PropertyCampaignStop.DEADLINE,
                        diagnostic = "Concrete result retained after the campaign or coverage collection timed out",
                    )
                }
                val concreteViolation = concrete.failure?.kind == PropertyFailureKind.PROPERTY
                if (concreteViolation) return result()
                if (concrete.failure?.kind == PropertyFailureKind.TIMEOUT) {
                    return result(PropertyCampaignStop.DEADLINE)
                }
                if (mode == PropertyCampaignMode.PBT_ONLY) return result()
            }

            phase = PropertyCampaignPhase.SYMBOLIC
            if (deadline.remainingMillis() <= validationReserveMillis) {
                return result(PropertyCampaignStop.DEADLINE)
            }

            val sources = captureSources(property, root)
            val frontendBudgetMillis = deadline.remainingMillis() - validationReserveMillis
            if (frontendBudgetMillis <= 0) return result(PropertyCampaignStop.DEADLINE)

            val scene = loadScene(root = root, timeoutMillis = frontendBudgetMillis)
            symbolic = search(
                property = property,
                root = root,
                scene = scene,
                deadline = deadline,
                validationReserveMillis = validationReserveMillis,
            )
            if (symbolic == null) return result(PropertyCampaignStop.DEADLINE)

            if (symbolic.target == null) {
                return if (symbolic.status == UsvmPropertySearchStatus.TIMEOUT) {
                    result(PropertyCampaignStop.DEADLINE)
                } else {
                    result()
                }
            }

            phase = PropertyCampaignPhase.CONFIRMATION
            val candidate = symbolic.captureWitness(property = property, sources = sources)

            confirmation = backend.confirmWitness(
                property = property,
                candidate = candidate,
                configuration = WitnessReplayConfiguration(
                    timeoutMillis = configuration.timeoutMillis,
                    validationReserveMillis = validationReserveMillis,
                    coverageRequest = configuration.coverageRequest,
                ),
                deadline = deadline,
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
        scene: EtsScene,
        deadline: WitnessReplayDeadline,
        validationReserveMillis: Long,
    ): UsvmPropertySearchResult? {
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
        )
    }

    private fun captureSources(property: PropertyDefinition, root: Path): List<PropertyObservationSource> {
        val modules = listOfNotNull(property.predicate.module, property.precondition?.module).distinct()

        return modules.map { module ->
            val path = root.resolve(module).toRealPath()
            require(path.startsWith(root)) { "Property module escapes its source root: $module" }

            val hash = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

            PropertyObservationSource(module = module, sha256 = hash)
        }
    }

    private companion object {
        const val DEFAULT_VALIDATION_RESERVE_MILLIS = 5_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val BYTE_MASK = 0xff
    }
}
