package org.usvm.ts.pbt.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId

/** Executes validated Kotlin property definitions through one concrete PBT engine. */
interface PropertyBasedTestingBackend {
    /** Reports whether this backend version can collect per-property source coverage. */
    val coverageCapability: PropertyCoverageCapability

    /** Executes [property] with [configuration] and returns a structured property result. */
    fun run(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ): PropertyRunResult
}

/** Backend-neutral controls for one concrete property run. */
data class PropertyRunConfiguration(
    val seed: Int? = null,
    val replayPath: String? = null,
    val numRuns: Int = DEFAULT_NUM_RUNS,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val examples: List<List<JsConcreteValue>> = emptyList(),
    val coverageRequest: PropertyCoverageRequest? = null,
) {
    init {
        require(numRuns > 0) { "Number of runs must be positive" }
        require(timeoutMillis > 0) { "Timeout must be positive" }
        require(timeoutMillis <= MAX_TIMEOUT_MILLIS) {
            "Timeout exceeds the maximum delay supported by Node timers"
        }
    }

    companion object {
        const val DEFAULT_NUM_RUNS = 100
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L

        /** Node timers use signed 32-bit millisecond delays. */
        val MAX_TIMEOUT_MILLIS: Long = Int.MAX_VALUE.toLong()
    }
}

/** Completion status; inspect [PropertyFailureKind] before interpreting a failure as a property violation. */
@Serializable
enum class PropertyRunStatus {
    @SerialName("success")
    SUCCESS,

    @SerialName("failure")
    FAILURE,
}

/** Stable classification of a completed property failure. */
@Serializable
enum class PropertyFailureKind {
    /** A predicate returned false or let an exception escape for one admitted input. */
    @SerialName("property")
    PROPERTY,

    /** No generated or explicit input was admitted before the backend skip limit was exhausted. */
    @SerialName("precondition-exhausted")
    PRECONDITION_EXHAUSTED,

    /** The concrete backend reported its configured time limit; this is not a property violation. */
    @SerialName("timeout")
    TIMEOUT,
}

/** Stable failure details that exclude runtime-dependent Node stack traces. */
@Serializable
data class PropertyFailureDetails(
    val kind: PropertyFailureKind,
    val errorName: String,
    val message: String,
) {
    init {
        require(errorName.isNotBlank()) { "Failure error name must not be blank" }
    }
}

/** Structured outcome of one concrete property run. */
@Serializable
data class PropertyRunResult(
    val propertyId: PropertyId,
    val status: PropertyRunStatus,
    val seed: Int,
    val replayPath: String?,
    val counterexample: List<JsConcreteValue>?,
    val numRuns: Int,
    val numSkips: Int,
    val numShrinks: Int,
    val failure: PropertyFailureDetails?,
    val executionTimeMillis: Long,
    val coverage: PropertyCoverageArtifact? = null,
) {
    init {
        require(numRuns >= 0) { "Run count must not be negative" }
        require(numSkips >= 0) { "Skip count must not be negative" }
        require(numShrinks >= 0) { "Shrink count must not be negative" }
        require(executionTimeMillis >= 0) { "Execution time must not be negative" }
        require(coverage == null || coverage.propertyId == propertyId) {
            "Coverage property ID must match the run result"
        }

        when (status) {
            PropertyRunStatus.SUCCESS -> {
                require(counterexample == null) { "A successful run must not contain a counterexample" }
                require(failure == null) { "A successful run must not contain failure details" }
            }

            PropertyRunStatus.FAILURE -> {
                val failureDetails = requireNotNull(failure) { "A failed run requires failure details" }

                when (failureDetails.kind) {
                    PropertyFailureKind.PROPERTY -> {
                        requireNotNull(counterexample) { "A property violation requires a counterexample" }
                    }

                    PropertyFailureKind.PRECONDITION_EXHAUSTED,
                    PropertyFailureKind.TIMEOUT,
                    -> require(counterexample == null) {
                        "A non-violation failure must not contain a counterexample"
                    }
                }
            }
        }
    }
}
