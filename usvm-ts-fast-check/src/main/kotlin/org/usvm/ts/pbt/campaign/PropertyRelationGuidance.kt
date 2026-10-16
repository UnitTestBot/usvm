package org.usvm.ts.pbt.campaign

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsScene
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ExternalWitnessCandidate
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.WitnessEvidenceDelta
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.inference.ScalarGuidanceCandidate
import org.usvm.ts.pbt.inference.ScalarGuidanceRelevance
import org.usvm.ts.pbt.inference.ScalarGuidanceSelection
import org.usvm.ts.pbt.inference.ScalarObservationPair
import org.usvm.ts.pbt.inference.ScalarRelationInference
import org.usvm.ts.pbt.inference.inferScalarRelations
import org.usvm.ts.pbt.inference.selectScalarGuidance
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationArtifact
import org.usvm.ts.pbt.usvm.UsvmDslNumericLocalRefuter
import org.usvm.ts.pbt.usvm.UsvmEntryRelationRefuter
import org.usvm.ts.pbt.usvm.UsvmPropertyProjectionCapability
import org.usvm.ts.pbt.usvm.UsvmRelationRefutationStatus
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Explicit opt-in; retained compiler authority is never reconstructed from source hashes or JSON. */
data class PropertyRelationGuidance(
    val pairs: List<ScalarObservationPair>,
    val compiled: CompiledDslProperty? = null,
    val allowUnknownRelevance: Boolean = false,
    val maxCandidates: Int = 16,
    val maxRelationMillis: Long = 5_000,
    val originalReserveMillis: Long = 5_000,
) {
    init {
        require(pairs.isNotEmpty()) { "Select at least one assertion operand pair" }
        require(maxCandidates > 0 && maxRelationMillis > 0 && originalReserveMillis > 0)
    }

    companion object {
        fun numericLocal(compiled: CompiledDslProperty): PropertyRelationGuidance {
            val capture = requireNotNull(compiled.numericLocalCapture) { "Compile the selected numeric local first" }
            return PropertyRelationGuidance(
                pairs = listOf(ScalarObservationPair(capture.result, capture.input)),
                compiled = compiled,
            )
        }
    }

    internal fun controls() = PropertyRelationGuidanceControls(
        pairs,
        compilerRetained = compiled != null,
        allowUnknownRelevance,
        maxCandidates,
        maxRelationMillis,
        originalReserveMillis,
    )
}

/** Serializable requested controls; the compiler object itself remains with its typed caller. */
@Serializable
data class PropertyRelationGuidanceControls(
    val pairs: List<ScalarObservationPair>,
    val compilerRetained: Boolean,
    val allowUnknownRelevance: Boolean,
    val maxCandidates: Int,
    val maxRelationMillis: Long,
    val originalReserveMillis: Long,
)

/** The selected relation stays an empirical hypothesis, including after successful refutation. */
@Serializable
data class PropertyRelationAttempt(
    val selected: ScalarGuidanceCandidate,
    val searchStatus: UsvmRelationRefutationStatus,
    val bindingStatus: String,
    val bindingDiagnostics: List<String>,
    val diagnostics: List<CapabilityDiagnostic>,
    val capability: UsvmPropertyProjectionCapability?,
    val witness: ExternalWitnessCandidate?,
    val confirmation: WitnessEvidenceDelta?,
    val elapsedMillis: Long,
    val completedWithinDeadline: Boolean,
)

/** Complete bounded selection and decisions from one PBT snapshot; no second random generation phase. */
@Serializable
data class PropertyRelationGuidanceTrace(
    val selection: ScalarGuidanceSelection?,
    val attempts: List<PropertyRelationAttempt>,
    val skippedUnknownRelevance: List<ScalarGuidanceCandidate>,
    val diagnostics: List<String>,
    val allocatedMillis: Long,
    val originalReserveMillis: Long,
    val elapsedMillis: Long,
    val originalAllocatedMillis: Long = 0,
    val originalElapsedMillis: Long = 0,
)

/** Reuses the existing refuters and confirmation lifecycle under a slice of the caller's deadline. */
internal class PropertyRelationChallenger(
    private val scene: EtsScene,
    private val mapping: PropertyEtsMappingArtifact,
    private val backend: FastCheckBackend,
    private val machineOptions: UMachineOptions,
    private val deadline: WitnessReplayDeadline,
    private val validationReserveMillis: Long,
) {
    fun challenge(
        property: PropertyDefinition,
        observations: PropertyObservationArtifact?,
        guidance: PropertyRelationGuidance,
        shrinkWitness: Boolean,
    ): PropertyRelationGuidanceTrace {
        val started = TimeSource.Monotonic.markNow()
        val attempts = mutableListOf<PropertyRelationAttempt>()
        val skipped = mutableListOf<ScalarGuidanceCandidate>()
        val diagnostics = mutableListOf<String>()
        val budget = minOf(
            guidance.maxRelationMillis,
            (deadline.remainingMillis() - validationReserveMillis - guidance.originalReserveMillis)
                .coerceAtLeast(minimumValue = 0),
        )
        val relationDeadline = WitnessReplayDeadline(timeoutMillis = budget.coerceAtLeast(minimumValue = 1))
        if (observations == null) {
            return PropertyRelationGuidanceTrace(
                selection = null,
                attempts,
                skipped,
                diagnostics = listOf("No PBT point observations were retained"),
                allocatedMillis = budget,
                originalReserveMillis = guidance.originalReserveMillis,
                elapsedMillis = started.elapsedNow().inWholeMilliseconds,
            )
        }

        val inference = inferScalarRelations(property, observations, guidance.pairs)
        val selection = selectScalarGuidance(inference, guidance.compiled, guidance.maxCandidates)
        for (selected in selection.candidates) {
            val unknown = selected.relevance == ScalarGuidanceRelevance.UNKNOWN
            if (unknown && !guidance.allowUnknownRelevance) {
                skipped += selected
                continue
            }
            if (budget <= 0 || relationDeadline.remainingMillis() <= 0) {
                diagnostics += "Relation allocation expired; unattempted candidates remain in the selection"
                break
            }

            val attempt = attempt(property, inference = inference, selected, guidance, relationDeadline, shrinkWitness)
            attempts += attempt
            if (attempt.confirmedOriginalFault() != null) break
        }

        return PropertyRelationGuidanceTrace(
            selection,
            attempts,
            skipped,
            diagnostics,
            allocatedMillis = budget,
            originalReserveMillis = guidance.originalReserveMillis,
            elapsedMillis = started.elapsedNow().inWholeMilliseconds,
        )
    }

    private fun attempt(
        property: PropertyDefinition,
        inference: ScalarRelationInference,
        selected: ScalarGuidanceCandidate,
        guidance: PropertyRelationGuidance,
        relationDeadline: WitnessReplayDeadline,
        shrinkWitness: Boolean,
    ): PropertyRelationAttempt {
        val started = TimeSource.Monotonic.markNow()
        val options = machineOptions.copy(
            timeout = minOf(
                machineOptions.timeout,
                relationDeadline.remainingMillis().coerceAtLeast(minimumValue = 1).milliseconds,
            ),
            stopOnCoverage = 0,
        )
        val relation = selected.relation
        val entryOnly = relation.pair.left.kind == ObservationPointKind.ENTRY_ARGUMENT &&
            relation.pair.right.kind == ObservationPointKind.ENTRY_ARGUMENT
        val concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT)
        val status: UsvmRelationRefutationStatus
        val bindingStatus: String
        val bindingDiagnostics: List<String>
        val diagnostics: List<CapabilityDiagnostic>
        val capability: UsvmPropertyProjectionCapability
        val witness: ExternalWitnessCandidate?

        if (entryOnly) {
            val result = UsvmEntryRelationRefuter(scene, options).refute(
                property.toManifest(),
                mapping,
                concreteCapability,
                inference,
                relation,
            )
            status = result.status
            bindingStatus = result.binding.status.name
            bindingDiagnostics = result.binding.diagnostics.map { "${it.code}:${it.sourcePath}:${it.message}" }
            diagnostics = result.diagnostics
            capability = result.capability
            witness = if (status.hasCandidate()) result.captureWitness() else null
        } else {
            val compiled = guidance.compiled
            if (compiled == null) {
                return PropertyRelationAttempt(
                    selected,
                    searchStatus = UsvmRelationRefutationStatus.UNSUPPORTED,
                    bindingStatus = "UNSUPPORTED",
                    bindingDiagnostics = listOf("Numeric local requires retained compiler output"),
                    diagnostics = emptyList(),
                    capability = null,
                    witness = null,
                    confirmation = null,
                    elapsedMillis = started.elapsedNow().inWholeMilliseconds,
                    completedWithinDeadline = relationDeadline.remainingMillis() > 0 && deadline.remainingMillis() > 0,
                )
            }
            val result = UsvmDslNumericLocalRefuter(scene, options).refute(
                compiled,
                mapping,
                concreteCapability,
                inference,
                relation,
            )
            status = result.status
            bindingStatus = result.binding.status.name
            bindingDiagnostics = result.binding.diagnostics.map { "${it.code}:${it.sourcePath}:${it.message}" }
            diagnostics = result.diagnostics
            capability = result.capability
            witness = if (status.hasCandidate()) result.captureWitness() else null
        }

        val native = confirm(property, witness, relationDeadline, shrinkWitness)

        return PropertyRelationAttempt(
            selected,
            status,
            bindingStatus,
            bindingDiagnostics,
            diagnostics + listOfNotNull(native.diagnostic),
            capability,
            witness,
            native.evidence,
            elapsedMillis = started.elapsedNow().inWholeMilliseconds,
            completedWithinDeadline = relationDeadline.remainingMillis() > 0 && deadline.remainingMillis() > 0,
        )
    }

    private fun confirm(
        property: PropertyDefinition,
        witness: ExternalWitnessCandidate?,
        deadline: WitnessReplayDeadline,
        shrinkWitness: Boolean,
    ): RelationConfirmation {
        if (witness == null || deadline.remainingMillis() <= 0) return RelationConfirmation(evidence = null)

        return try {
            val evidence = backend.confirmWitness(
                property,
                witness,
                WitnessReplayConfiguration(
                    timeoutMillis = deadline.remainingMillis().coerceAtLeast(minimumValue = 1),
                    validationReserveMillis = validationReserveMillis,
                    shrink = shrinkWitness,
                    coverageRequest = PropertyCoverageRequest(admittedOnly = true),
                ),
                deadline,
            )
            RelationConfirmation(evidence = evidence)
        } catch (error: PbtBackendException) {
            RelationConfirmation(
                evidence = null,
                diagnostic = CapabilityDiagnostic(error.code, error.message.orEmpty(), path = "relation.confirmation"),
            )
        } catch (error: IOException) {
            RelationConfirmation(
                evidence = null,
                diagnostic = CapabilityDiagnostic(
                    "campaign.relation.io",
                    error.toString(),
                    path = "relation.confirmation",
                ),
            )
        }
    }
}

private fun UsvmRelationRefutationStatus.hasCandidate(): Boolean = when (this) {
    UsvmRelationRefutationStatus.REFUTATION_REACHED,
    UsvmRelationRefutationStatus.FAILED_INPUT_RESOLUTION,
    -> true
    else -> false
}

private data class RelationConfirmation(
    val evidence: WitnessEvidenceDelta?,
    val diagnostic: CapabilityDiagnostic? = null,
)

internal fun PropertyRelationGuidanceTrace.confirmedOriginalFault(): WitnessEvidenceDelta? =
    attempts.firstNotNullOfOrNull { it.confirmedOriginalFault() }

private fun PropertyRelationAttempt.confirmedOriginalFault(): WitnessEvidenceDelta? {
    if (!completedWithinDeadline) return null

    return confirmation?.takeIf { it.status == WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION }
}
