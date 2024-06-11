package com.spbpu.bbfinfrastructure.sarif

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ResultSarifBuilder {
    private val json = Json { prettyPrint = true }

    fun deserialize(sarif: String): ResultSarif = json.decodeFromString<ResultSarif>(sarif)

    @Serializable
    data class ResultSarif(
        val runs: List<ResultRun>,
        val version: String? = null,
        val `$schema`: String? = null
    )

    @Serializable
    data class ResultRun(
        val results: List<ResultResult>,
        val tool: ResultTool
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
        val locations: List<ResultLocation>,
        val message: ResultMessage,
        val ruleId: String,
        val kind: String? = null
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


