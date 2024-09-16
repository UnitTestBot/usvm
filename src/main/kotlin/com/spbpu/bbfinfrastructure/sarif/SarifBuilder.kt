package com.spbpu.bbfinfrastructure.sarif

import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder.ResultRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SarifBuilder {

    private val json = Json { prettyPrint = true }

    fun serialize(suiteProjects: List<Pair<Project, List<MutationInfo>>>, driverName: String): String {
        val sarif = Sarif(
            `$schema` = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            version = "2.1.0",
            runs = listOf(
                Run(
                    tool = Tool(
                        driver = Driver(
                            name = driverName
                        )
                    ),
                    results = suiteProjects.map { buildFakeResult(it.first) }
                )
            )
        )

        return json.encodeToString(sarif)
    }

    fun serializeRealResults(files: List<Triple<String, String, Int>>, prefix: String, driverName: String): String {
        val sarif = Sarif(
            `$schema` = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            version = "2.1.0",
            runs = listOf(
                Run(
                    tool = Tool(
                        driver = Driver(
                            name = driverName
                        )
                    ),
                    results = files.map { buildRealResult(it.first, it.third, it.second) }
                )
            )
        )

        return json.encodeToString(sarif)
    }

    private fun buildFakeResult(project: Project): Result {
        val relativePath = project.configuration.mutatedUri ?: project.configuration.sourceFileName
        val resultRegion = project.configuration.mutatedRegion
        return Result(
            kind = "fail",
            message = Message(
                text = "message"
            ),
            ruleId = "CWE-777",
            locations = listOf(
                Location(
                    physicalLocation = PhysicalLocation(
                        artifactLocation = ArtifactLocation(
                            uri = relativePath
                        ),
                        region = resultRegion
                    )
                )
            )
        )
    }


    private fun buildRealResult(uri: String, cwe: Int, kind: String): Result {
        return Result(
            kind = kind,
            message = Message(
                text = "message"
            ),
            ruleId = "CWE-$cwe",
            locations = listOf(
                Location(
                    physicalLocation = PhysicalLocation(
                        artifactLocation = ArtifactLocation(
                            uri = uri
                        ),
                        region = null
                    )
                )
            )
        )
    }

    @Serializable
    data class Sarif(
        val `$schema`: String,
        val version: String,
        val runs: List<Run>
    )

    @Serializable
    data class Run(
        val tool: Tool,
        val results: List<Result>
    )

    @Serializable
    data class Tool(
        val driver: Driver
    )

    @Serializable
    data class Driver(
        val name: String
    )

    @Serializable
    data class Result(
        val kind: String,
        val message: Message,
        val ruleId: String,
        val locations: List<Location>
    )

    @Serializable
    data class Message(
        val text: String
    )

    @Serializable
    data class Location(
        val physicalLocation: PhysicalLocation
    )

    @Serializable
    data class PhysicalLocation(
        val artifactLocation: ArtifactLocation,
        val region: ResultRegion? = null
    )

    @Serializable
    data class ArtifactLocation(
        val uri: String
    )
}

