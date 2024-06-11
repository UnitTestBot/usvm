package com.spbpu.bbfinfrastructure.sarif

import kotlinx.serialization.Serializable

class MarkupSarif {


    @Serializable
    data class Sarif(
        val version: String,
        val `$schema`: String,
        val results: List<Result>
    )

    @Serializable
    data class Result(
        val location: String,
        val kind: String,
        val ruleId: String,
        val toolsResults: List<ToolResult>
    )

    @Serializable
    data class ToolResult(
        val toolName: String,
        val isWorkCorrectly: String
    )

}