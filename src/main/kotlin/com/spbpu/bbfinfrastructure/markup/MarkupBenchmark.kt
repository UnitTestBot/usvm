package com.spbpu.bbfinfrastructure.markup

import com.spbpu.bbfinfrastructure.sarif.MarkupSarif
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.util.CweUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MarkupBenchmark {

    fun markup(
        pathToGroundTruth: String,
        toolsResultsPaths: List<String>,
        pathToResultSarif: String
    ) {
        val toolsResultsSarifBuilder = ToolsResultsSarifBuilder()
        val groundTruth = toolsResultsSarifBuilder
            .deserialize(File(pathToGroundTruth).readText())
            .runs.first().results
            .filter { it.kind == "fail" }
            .associateWith { mutableSetOf<String>() }
            .toMutableMap()
        val tools = toolsResultsPaths.map { it.substringAfterLast('/').substringBefore('_') }
        toolsResultsPaths.forEach { toolResultPath ->
            val toolResults = File(toolResultPath).readText()
            val toolName = toolResultPath.substringAfterLast('/').substringBefore('_')
            val decodedToolResult = toolsResultsSarifBuilder.deserialize(toolResults)
            var i = 0
            for (toolRes in decodedToolResult.runs.first().results) {
                println("Handle res ${i++} from ${decodedToolResult.runs.first().results.size} for tool $toolName")
                val resultFromGroundTruth =
                    groundTruth.keys.find { toolRes.locations.first().isIn(it.locations.first()) }
                if (resultFromGroundTruth != null) {
                    val groundTruthKind = resultFromGroundTruth.kind!!
                    if (groundTruthKind == "fail") {
                        val groundTruthCwes = resultFromGroundTruth.ruleId.split(",").map { it.substringAfter("CWE-").toInt() }
                        val toolsFoundCWE = toolRes.ruleId.split(',').map { it.substringAfter("CWE-").toInt() }.toSet()
                        val groundTruthCweWithChildren = groundTruthCwes.flatMap { CweUtil.getCweChildrenOf(it) } + groundTruthCwes
                        if (groundTruthCweWithChildren.intersect(toolsFoundCWE).isNotEmpty()) {
                            if (groundTruth[resultFromGroundTruth]?.all { !it.contains(toolName) } == true) {
                                groundTruth[resultFromGroundTruth]?.add("${toolName}: true")
                            }
                        }
                    }
                }
            }
        }
        val iterator = groundTruth.iterator()
        while (iterator.hasNext()) {
            val gtValue = iterator.next()
            if (gtValue.value.isEmpty()) {
                iterator.remove()
            }
        }
        val markupSarif = MarkupSarif.Sarif(
            `$schema` = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            version = "2.1.0",
            results = groundTruth.entries.mapNotNull { (cweInfo, toolResults) ->
                val (cwe, kind) = cweInfo.let { it.ruleId to it.kind!! }
                tools.forEach { toolName ->
                    if (toolResults.all { !it.contains(toolName) }) {
                        toolResults.add("$toolName: false")
                    }
                }
                MarkupSarif.Result(
                    location = cweInfo.locations.first(),
                    kind = kind,
                    ruleId = cwe,
                    toolsResults = toolResults.map {
                        MarkupSarif.ToolResult(
                            toolName = it.substringBefore(": "),
                            isWorkCorrectly = it.substringAfter(": ")
                        )
                    }
                )
            }
        )
        File(pathToResultSarif).writeText(Json { prettyPrint = true }.encodeToString(markupSarif))
    }

}