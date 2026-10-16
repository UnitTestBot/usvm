package org.usvm.ts.pbt.backend

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue

/** Caller-selected input and lineage; this DTO does not certify confirmation by the original oracle. */
@Serializable
data class ReturnedInputSeed(
    val id: String,
    val inputs: List<JsConcreteValue>,
    val sourceRunId: String,
    val sourceInvocationId: Int,
)

/**
 * Bounded native integer neighborhoods, mixed equally with the unchanged original generator.
 * Requires bounded observation capture to retain lineage; seed admission is the caller's responsibility.
 */
@Serializable
data class ReturnedInputGeneration(
    val seeds: List<ReturnedInputSeed>,
    val radius: Int = 1,
)
