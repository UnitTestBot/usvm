package org.usvm.ts.pbt.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId

/** Executes validated Kotlin property definitions through one concrete PBT engine. */
interface PropertyBasedTestingBackend {
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
) {
    init {
        require(numRuns > 0) { "Number of runs must be positive" }
        require(timeoutMillis > 0) { "Timeout must be positive" }
    }

    companion object {
        const val DEFAULT_NUM_RUNS = 100
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}

/** Whether the predicate held for every value executed by the backend. */
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
    @SerialName("property")
    PROPERTY,

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
        require(message.isNotBlank()) { "Failure message must not be blank" }
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
) {
    init {
        require(numRuns >= 0) { "Run count must not be negative" }
        require(numSkips >= 0) { "Skip count must not be negative" }
        require(numShrinks >= 0) { "Shrink count must not be negative" }
        require(executionTimeMillis >= 0) { "Execution time must not be negative" }
        when (status) {
            PropertyRunStatus.SUCCESS -> {
                require(counterexample == null) { "A successful run must not contain a counterexample" }
                require(failure == null) { "A successful run must not contain failure details" }
            }

            PropertyRunStatus.FAILURE -> {
                requireNotNull(failure) { "A failed run requires failure details" }
            }
        }
    }
}
