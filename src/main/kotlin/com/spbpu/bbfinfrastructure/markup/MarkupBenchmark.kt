package com.spbpu.bbfinfrastructure.markup

import com.spbpu.bbfinfrastructure.sarif.MarkupSarif
import com.spbpu.bbfinfrastructure.sarif.ResultSarifBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.StringBuilder
import kotlin.streams.*

class MarkupBenchmark {

    fun markup(
        pathToGroundTruth: String,
        pathToSrc: String,
        toolsResultsPaths: List<String>
    ) {
        val srcFiles = Files.walk(Paths.get(pathToSrc)).map { it.toFile() }.toList().filter { it.isFile }
        val resultSarifBuilder = ResultSarifBuilder()
        val groundTruth = resultSarifBuilder
            .deserialize(File(pathToGroundTruth).readText())
            .runs.first().results
            .associate {
                it.locations.first().physicalLocation.artifactLocation.uri to Pair(
                    it.ruleId.substringAfter("CWE-"),
                    it.kind
                )
            }
        val headers = mutableMapOf<String, StringBuilder>()
        val tools = toolsResultsPaths.map { it.substringBefore('_').substringAfter('/') }
        toolsResultsPaths.forEach { toolResultPath ->
            val toolResults = File(toolResultPath).readText()
            val decodedToolResult = resultSarifBuilder.deserialize(toolResults)
            val toolName = toolResultPath.substringBefore('_').substringAfter('/')
            val groupedResults = decodedToolResult.runs.first().results.groupBy(
                { it.locations.first().physicalLocation.artifactLocation.uri },
                { it.ruleId.substringAfter("CWE-") }
            ).mapValues { it.value.toSet() }
            groupedResults.forEach { (benchmarkName, foundBugs) ->
                val (groundTruthCWE, groundTruthKind) = groundTruth[benchmarkName] ?: return@forEach
                if (groundTruthKind == null) return@forEach
                val toolPass =
                    if (groundTruthKind == "pass") {
                        !foundBugs.contains(groundTruthCWE)
                    } else {
                        foundBugs.contains(groundTruthCWE)
                    }
                if (headers.containsKey(benchmarkName)) {
                    headers[benchmarkName]!!.append("\n${toolName}: $toolPass")
                } else {
                    headers[benchmarkName] = StringBuilder("${toolName}: $toolPass")
                }
            }
        }
        srcFiles.forEach { file ->
            val fileName = file.name
            if (headers.keys.all { !it.contains(fileName) }) {
                file.delete()
            }
            val header = headers.entries.find { it.key.contains(fileName) }?.key ?: return@forEach

            if (!headers[header]!!.contains("true")) {
                file.delete()
            }
        }

        val markupSarif = MarkupSarif.Sarif(
            `$schema` = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            version = "2.1.0",
            results = headers.entries.mapNotNull { (benchmarkName, header) ->
                val (cwe, kind) = groundTruth[benchmarkName] ?: return@mapNotNull null
                tools.forEach { toolName ->
                    if (!header.contains(toolName)) {
                        header.append("\n${toolName}: false")
                    }
                }
                MarkupSarif.Result(
                    location = benchmarkName,
                    kind = kind!!,
                    ruleId = cwe,
                    toolsResults = header.split("\n").map { toolRes ->
                        MarkupSarif.ToolResult(
                            toolName = toolRes.substringBefore(":"),
                            isWorkCorrectly = toolRes.substringAfter(": ")
                        )
                    }

                )
            }
        )
        File("lib/tools_truth.sarif").writeText(Json.encodeToString(markupSarif))
    }

}