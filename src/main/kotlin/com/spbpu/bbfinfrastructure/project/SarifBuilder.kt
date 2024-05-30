package com.spbpu.bbfinfrastructure.project

import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SarifBuilder {

    private val json = Json { prettyPrint = true }

    fun serialize(suiteProjects: List<Pair<Project, List<MutationInfo>>>): String {
        val sarif = Sarif(
            `$schema` = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            version = "2.1.0",
            runs = listOf(
                Run(
                    tool = Tool(
                        driver = Driver(
                            name = "flawgarden-BenchmarkJava-mutated-demo"
                        )
                    ),
                    results = suiteProjects.map { buildResult(it.first) }
                )
            )
        )

        return json.encodeToString(sarif)
    }

    private fun buildResult(project: Project): Result {
        val localPaths = project.saveToDir(CompilerArgs.tmpPath)
        val fileName = localPaths.find { it.contains("Benchmark") } ?: error("Can't find Benchmark file")
        val nameWithoutExt = fileName.substringAfterLast('/').substringBefore(".java")
        val relativePath = "src/main/java/org/owasp/benchmark/testcode/$nameWithoutExt.java"
        val originalFileName = "BenchmarkTest" + fileName.substringAfter("BenchmarkTest").take(5)
        val originalExpectedResults = File("lib/BenchmarkJavaTemplate/expectedresults-1.2.csv").readText()
        val originExpectedResults =
            originalExpectedResults.split("\n").find { it.startsWith(originalFileName) }!!
        val cweToFind = originExpectedResults.substringAfterLast(',').toInt()
        return Result(
            kind = "fail",
            message = Message(
                text = "sqli"
            ),
            ruleId = "CWE-$cweToFind",
            locations = listOf(
                Location(
                    physicalLocation = PhysicalLocation(
                        artifactLocation = ArtifactLocation(
                            uri = relativePath
                        )
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
        val artifactLocation: ArtifactLocation
    )

    @Serializable
    data class ArtifactLocation(
        val uri: String
    )
}

