package com.spbpu.bbfinfrastructure.sarif

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ResultSarifBuilder {
    private val json = Json { prettyPrint = true }

    fun deserialize(sarif: String): ResultSarif = json.decodeFromString<ResultSarif>(sarif)

    @Serializable
    data class ResultSarif(
        val `$schema`: String? = null,
        val version: String? = null,
        val runs: List<ResultRun>
    )

    @Serializable
    data class ResultRun(
        val tool: ResultTool,
        val results: List<ResultResult>,
    )

    @Serializable
    data class ResultTool(
        val driver: ResultDriver
    )

    @Serializable
    data class ResultDriver(
        val name: String
    )

    @Serializable
    data class ResultResult(
        val kind: String? = null,
        val message: ResultMessage,
        val ruleId: String,
        val locations: List<ResultLocation>,
    )

    @Serializable
    data class ResultMessage(
        val text: String
    )

    @Serializable
    data class ResultLocation(
        val physicalLocation: ResultPhysicalLocation
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


