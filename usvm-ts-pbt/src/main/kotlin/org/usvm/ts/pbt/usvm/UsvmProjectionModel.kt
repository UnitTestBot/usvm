package org.usvm.ts.pbt.usvm

import kotlinx.serialization.Serializable
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.PropertyCapabilityLevel

/** Bounds resource usage while recursively materializing symbolic property inputs. */
data class UsvmProjectionOptions(
    val maxSymbolicCollectionLength: Int = 10,
    val maxSymbolicStringLength: Int = TsOptions().maxArraySize,
) {
    init {
        require(maxSymbolicStringLength >= 0) { "Maximum symbolic string length must be non-negative" }

        require(maxSymbolicCollectionLength >= 0) {
            "Maximum symbolic collection length must be non-negative"
        }
    }
}

/** Capability of one ordered property input at its stable manifest path. */
@Serializable
data class UsvmInputProjectionCapability(
    val inputName: String,
    val path: String,
    val capability: ProjectionCapability,
)

/** Combined concrete and symbolic execution capability for one property. */
@Serializable
data class UsvmPropertyProjectionCapability(
    val inputs: List<UsvmInputProjectionCapability>,
    val precondition: ProjectionCapability,
    val symbolic: ProjectionCapability,
    val property: PropertyCapabilityLevel,
)
