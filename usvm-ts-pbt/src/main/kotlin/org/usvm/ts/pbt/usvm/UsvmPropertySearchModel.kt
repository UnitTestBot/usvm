package org.usvm.ts.pbt.usvm

import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyId

/** Terminal outcome of one USVM property-violation search. */
enum class UsvmPropertySearchStatus {
    VIOLATION_REACHED,
    NO_VIOLATION_REACHED,
    PRECONDITION_REJECTED,
    PROPERTY_ERROR,
    TIMEOUT,
    SOLVER_UNKNOWN,
    UNSUPPORTED,
    ENGINE_FAILURE,
    FAILED_INPUT_RESOLUTION,
}

/** Supported ways in which a mapped predicate can violate its property. */
enum class UsvmPropertyViolationTarget {
    PREDICATE_FALSE,
    UNEXPECTED_EXCEPTION,
    ASSERTION_FAILURE,
}

/** Backend-neutral result of searching one mapped TypeScript property with USVM. */
data class UsvmPropertySearchResult(
    val propertyId: PropertyId,
    val status: UsvmPropertySearchStatus,
    val target: UsvmPropertyViolationTarget?,
    val inputs: List<JsConcreteValue>?,
    val capability: UsvmPropertyProjectionCapability,
    val diagnostics: List<CapabilityDiagnostic>,
) {
    init {
        when (status) {
            UsvmPropertySearchStatus.VIOLATION_REACHED -> {
                requireNotNull(target) { "A reached violation requires its target" }
                requireNotNull(inputs) { "A resolved violation requires candidate inputs" }
            }

            UsvmPropertySearchStatus.FAILED_INPUT_RESOLUTION -> {
                requireNotNull(target) { "An input-resolution failure requires its reached target" }
                require(inputs == null) { "An input-resolution failure cannot contain candidate inputs" }
            }

            else -> {
                require(target == null) { "A result without a violation cannot contain a target" }
                require(inputs == null) { "A result without a violation cannot contain candidate inputs" }
            }
        }
    }
}
