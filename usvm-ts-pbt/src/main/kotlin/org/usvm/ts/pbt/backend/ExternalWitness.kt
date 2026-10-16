package org.usvm.ts.pbt.backend

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.inference.ScalarRelationTemplate
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.observation.CompilerNumericLocalBinding
import org.usvm.ts.pbt.observation.PropertyObservationSource

/** Auxiliary witnesses are useful evidence even when the complete original oracle holds. */
@Serializable
enum class WitnessRole { PROPERTY, COVERAGE, HYPOTHESIS }

/** Adapter-owned comparison of two original numeric inputs, before the original callback runs. */
@Serializable
data class WitnessEntryRelation(
    val leftInputIndex: Int,
    val rightInputIndex: Int,
    val template: ScalarRelationTemplate,
) {
    init {
        require(leftInputIndex >= 0 && rightInputIndex >= 0)
    }

    companion object {
        const val CONTEXT_ID = "original-entry"
    }
}

/** Explicit companion callback, comparing one stored numeric result with an original input snapshot. */
@Serializable
data class WitnessDslNumericLocalRelation(
    val compiler: CompilerNumericLocalBinding,
    val inputIndex: Int,
    val resultIsLeft: Boolean,
    val template: ScalarRelationTemplate,
) {
    init {
        require(inputIndex >= 0)
    }

    companion object {
        const val CONTEXT_ID = "original-compiler-local"
    }
}

/** Exact executable hook binding supplied by a producer, never inferred from a source line. */
@Serializable
data class WitnessBinding(
    val pointId: String,
    val contextId: String,
    val revision: String,
    val occurrence: Int = 0,
) {
    init {
        require(pointId.isNotBlank() && contextId.isNotBlank() && revision.isNotBlank())
        require(occurrence >= 0)
    }
}

/** An original named check or a guarded auxiliary target within the complete original predicate. */
@Serializable
data class WitnessTarget(
    val id: String,
    val role: WitnessRole = WitnessRole.PROPERTY,
    val binding: WitnessBinding? = null,
    val expectedOutcome: WitnessOracleOutcome? = null,
    val entryRelation: WitnessEntryRelation? = null,
    val dslNumericLocalRelation: WitnessDslNumericLocalRelation? = null,
) {
    init {
        require(id.isNotBlank())
        require(role == WitnessRole.PROPERTY || binding != null)
        if (entryRelation != null) {
            require(role == WitnessRole.HYPOTHESIS)
            require(binding?.contextId == WitnessEntryRelation.CONTEXT_ID && binding.occurrence == 0)
        }
        if (dslNumericLocalRelation != null) {
            require(entryRelation == null && role == WitnessRole.HYPOTHESIS)
            require(binding?.contextId == WitnessDslNumericLocalRelation.CONTEXT_ID && binding.occurrence == 0)
        }
        require(
            expectedOutcome == null || expectedOutcome == WitnessOracleOutcome.FALSE ||
                expectedOutcome == WitnessOracleOutcome.THREW
        )
    }
}

/** Unconfirmed input and the exact registration/source revisions used to extract it. */
@Serializable
data class ExternalWitnessCandidate(
    val propertyId: PropertyId,
    val target: WitnessTarget,
    val inputs: List<JsConcreteValue>?,
    val manifestSha256: String,
    val manifest: PropertyManifest,
    val sources: List<PropertyObservationSource>,
    val provenance: String,
)

/** Concrete admission is separate from the original property outcome. */
@Serializable
enum class WitnessAdmission { ADMITTED, DOMAIN_REJECTED, PRECONDITION_REJECTED, ERROR, UNKNOWN }

/** Completion of the complete original predicate, including escaping assertion exceptions. */
@Serializable
enum class WitnessOracleOutcome { HOLDS, FALSE, THREW, SKIPPED, ERROR, UNKNOWN }

/** One guarded event produced during this original predicate invocation. */
@Serializable
data class WitnessEvent(
    val binding: WitnessBinding,
    val guard: Boolean,
    val holds: Boolean,
)

/** A replay result is not a final fault until the coordinator confirms repeatability and target identity. */
@Serializable
data class WitnessNativeReplay(
    val admission: WitnessAdmission,
    val outcome: WitnessOracleOutcome,
    val targetMatched: Boolean,
    val events: List<WitnessEvent> = emptyList(),
    val failure: PropertyFailureDetails? = null,
    val diagnostic: String? = null,
    val coverage: PropertyCoverageArtifact? = null,
    val runtimeVersion: String? = null,
)

/** Scheduler-visible classification; only confirmed original violations count as faults. */
@Serializable
enum class WitnessReplayStatus {
    CONFIRMED_PROPERTY_VIOLATION,
    CONFIRMED_HYPOTHESIS_REFUTATION,
    CONFIRMED_COVERAGE,
    PROPERTY_HOLDS,
    TARGET_MISMATCH,
    DOMAIN_REJECTED,
    PRECONDITION_REJECTED,
    UNREPRESENTABLE_INPUT,
    UNSUPPORTED_REPLAY,
    SOURCE_MISMATCH,
    NONDETERMINISTIC,
    PROPERTY_ERROR,
    REPLAY_ERROR,
    TIMEOUT,
}

/** Reduction failure never invalidates or replaces the original confirmed witness. */
@Serializable
enum class WitnessReductionStatus { NOT_ELIGIBLE, DISABLED, UNSUPPORTED, EXHAUSTED, FAILED, TIMEOUT }

/** EXHAUSTED means the backend stream was exhausted, not a global minimality proof. */
@Serializable
data class WitnessReduction(
    val status: WitnessReductionStatus,
    val inputs: List<JsConcreteValue>? = null,
    val attempts: Int = 0,
    val acceptedShrinks: Int = 0,
    val originalEncodedBytes: Int = 0,
    val reducedEncodedBytes: Int? = null,
    val diagnostic: String? = null,
)

/** Source-qualified edge identity; only the exact native two-arm branch collector may produce it. */
@Serializable
data class WitnessCoverageEdge(
    val source: PropertyObservationSource,
    val location: SourceRange,
    val outcome: Boolean,
)

/** Immutable evidence delta for the shared scheduler. Rejected replay never removes a coverage goal. */
@Serializable
data class WitnessEvidenceDelta(
    val candidate: ExternalWitnessCandidate,
    val status: WitnessReplayStatus,
    val originalReplay: WitnessNativeReplay? = null,
    val reducedReplay: WitnessNativeReplay? = null,
    val reduction: WitnessReduction = WitnessReduction(status = WitnessReductionStatus.NOT_ELIGIBLE),
    val confirmedEdgeAdditions: Set<WitnessCoverageEdge> = emptySet(),
    val contextualRefutations: List<WitnessEvent> = emptyList(),
    val backendId: String,
    val backendVersion: String,
    val adapterProtocol: String = "external-witness-v1",
    val diagnostic: String? = null,
)

/** Backend-neutral external-candidate entry point; it does not launch a new symbolic search. */
interface ExternalWitnessBackend {
    fun confirmWitness(
        property: org.usvm.ts.pbt.model.PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        configuration: WitnessReplayConfiguration = WitnessReplayConfiguration(),
        deadline: WitnessReplayDeadline = WitnessReplayDeadline(configuration.timeoutMillis),
    ): WitnessEvidenceDelta
}

/** Execution controls and the caller's already confirmed source-qualified edges. */
data class WitnessReplayConfiguration(
    val timeoutMillis: Long = 60_000L,
    val validationReserveMillis: Long = 5_000L,
    val shrink: Boolean = true,
    val coverageRequest: PropertyCoverageRequest? = null,
    val knownEdges: Set<WitnessCoverageEdge> = emptySet(),
) {
    init {
        require(timeoutMillis in 1..PropertyRunConfiguration.MAX_TIMEOUT_MILLIS)
        require(validationReserveMillis > 0)
    }
}

/** A monotonic budget that callers may create before search and share with replay/reduction. */
class WitnessReplayDeadline(timeoutMillis: Long) {
    private val startedAt = System.nanoTime()
    private val budgetNanos = timeoutMillis * NANOS_PER_MILLISECOND

    init {
        require(timeoutMillis in 1..PropertyRunConfiguration.MAX_TIMEOUT_MILLIS)
    }

    fun remainingMillis(): Long {
        val elapsedNanos = System.nanoTime() - startedAt
        return ((budgetNanos - elapsedNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(0)
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
