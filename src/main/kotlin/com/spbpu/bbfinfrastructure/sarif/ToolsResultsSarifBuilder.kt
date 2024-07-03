package com.spbpu.bbfinfrastructure.sarif

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ToolsResultsSarifBuilder {
    private val json = Json { prettyPrint = true }

    fun deserialize(sarif: String): ToolResultSarif = json.decodeFromString<ToolResultSarif>(sarif)

    @Serializable
    data class ToolResultSarif(
        val `$schema`: String? = null,
        val version: String? = null,
        val runs: List<ToolRun>
    )

    @Serializable
    data class ToolRun(
        val tool: ToolInfo,
        val results: List<ToolExecutionResult>,
    )

    @Serializable
    data class ToolInfo(
        val driver: ToolDriver
    )

    @Serializable
    data class ToolDriver(
        val name: String,
        val informationUri: String? = null,
        val organization: String? = null,
        val version: String? = null
    )

    @Serializable
    data class ToolExecutionResult(
        val kind: String? = null,
        val message: ToolResultMessage,
        val ruleId: String,
        val locations: List<ResultLocation>,
        val codeFlows: List<String>? = null,
        val level: String? = null
    )

    @Serializable
    data class ToolResultMessage(
        val text: String
    )

    @Serializable
    data class ResultLocation(
        val physicalLocation: ResultPhysicalLocation,
        val logicalLocations: List<ResultLogicalLocation>? = null
    ) {
        fun isIn(other: ResultLocation): Boolean {
            if (physicalLocation.artifactLocation.uri != other.physicalLocation.artifactLocation.uri) return false
            when {
                physicalLocation.region == null && other.physicalLocation.region == null -> return true
                physicalLocation.region != null && other.physicalLocation.region == null -> return true
                physicalLocation.region == null && other.physicalLocation.region != null -> return false
            }
            val location = physicalLocation.region!!
            val otherLocation = other.physicalLocation.region!!
            if (otherLocation.startLine!! <= location.startLine!!) {
                if (otherLocation.endLine == null || location.endLine == null) {
                    return true
                }
                if (otherLocation.endLine >= location.endLine) {
                    return true
                }
            }
            return false
        }
    }

    @Serializable
    data class ResultLogicalLocation(
        val fullyQualifiedName: String? = null
    )

    @Serializable
    data class ResultPhysicalLocation(
        val artifactLocation: ResultArtifactLocation,
        val region: ResultRegion? = null
    )

    @Serializable
    data class ResultArtifactLocation(
        val uri: String
    )

    @Serializable
    data class ResultRegion(
        val endColumn: Int? = null,
        val startColumn: Int? = null,
        val startLine: Int? = null,
        val endLine: Int? = null
    )
}


