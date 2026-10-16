package org.usvm.ts.pbt.observation

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.PropertySourcePoint

/** Compiler-owned point transport; selected source bytes do not verify its executable build or imports. */
@Serializable
data class CompilerNumericLocalBinding(
    val producer: String,
    val abi: String,
    val programId: String,
    val nodeId: Int,
    val localName: String,
    val predicateExportName: String,
    val companionExportName: String,
    val predicateAssignment: PropertySourcePoint,
    val companionAssignment: PropertySourcePoint,
    val sourceSha256: String,
    val occurrence: Int,
) {
    companion object {
        const val PRODUCER = "usvm-kotlin-dsl-v1"
        const val ABI = "numeric-local-callback-v1"
    }
}
