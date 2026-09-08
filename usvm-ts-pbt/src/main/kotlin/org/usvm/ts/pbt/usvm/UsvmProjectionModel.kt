package org.usvm.ts.pbt.usvm

import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.PropertyCapabilityLevel

/** Bounds resource usage while recursively materializing symbolic property inputs. */
data class UsvmProjectionOptions(
    val maxSymbolicCollectionLength: Int = 10,
) {
    init {
        require(maxSymbolicCollectionLength >= 0) {
            "Maximum symbolic collection length must be non-negative"
        }
    }
}

/** Capability of one ordered property input at its stable manifest path. */
data class UsvmInputProjectionCapability(
    val inputName: String,
    val path: String,
    val capability: ProjectionCapability,
)

/** Combined concrete and symbolic execution capability for one property. */
data class UsvmPropertyProjectionCapability(
    val inputs: List<UsvmInputProjectionCapability>,
    val precondition: ProjectionCapability,
    val symbolic: ProjectionCapability,
    val property: PropertyCapabilityLevel,
)
