package com.spbpu.bbfinfrastructure.util.results

import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
import com.spbpu.bbfinfrastructure.sarif.MarkupSarif
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.util.CweUtil
import com.spbpu.bbfinfrastructure.util.getRandomVariableName
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.io.File
import kotlin.random.Random

object ScoreCardParser {

    fun parseAndSaveDiff(
        scorecardsDir: String,
        pathToSources: String,
        pathToToolsGroundTruthSarif: String
    ) {
        val toolsResultsSarifBuilder = ToolsResultsSarifBuilder()
        val scorecards = File(scorecardsDir).listFiles().filter { it.path.endsWith(".sarif") }
        val toolsGroundTruth = Json.decodeFromString<MarkupSarif.Sarif>(File(pathToToolsGroundTruthSarif).readText())
        for (p in GlobalTestSuite.javaTestSuite.suiteProjects) {
            //For potential saving
            val originalResults = mutableListOf<Pair<String, Set<Int>>>()
            val analysisResults = mutableListOf<Pair<String, Set<Int>>>()

            val originalLocation = p.first.configuration.getOriginalLocation()
            val mutatedLocation = p.first.configuration.getMutatedLocation()
            val mutatedUri = p.first.configuration.mutatedUri!!
            val originalUri = p.first.configuration.originalUri!!
            val originalCwes =
                p.first.configuration.initialCWEs.flatMap { listOf(it) + CweUtil.getCweChildrenOf(it) }.toSet()
            val resultsFromGroundTruth =
                toolsGroundTruth.results.find { it.location.isIn(originalLocation) } ?: continue

            var diffPoints = 0
            for (scorecard in scorecards) {
                val decodedSarif = toolsResultsSarifBuilder.deserialize(scorecard.readText())
                val toolName = scorecard.name.substringBefore('_')
                val resultsForProject = decodedSarif.runs.first().results
                    .filter { it.locations.first().isIn(mutatedLocation) }
                    .flatMap { it.ruleId.split(",").map { it.trim().substringAfter("CWE-").toInt() } }
                    .toSet()
                analysisResults.add(toolName to resultsForProject)
                val isFound = originalCwes.intersect(resultsForProject).isNotEmpty()
                val isCorrect =
                    when {
                        resultsFromGroundTruth.kind == "fail" && isFound -> true
                        resultsFromGroundTruth.kind == "pass" && !isFound -> false
                        else -> false
                    }
                val resultFromGroundTruthForTool =
                    resultsFromGroundTruth.toolsResults.find { it.toolName == toolName } ?: continue

                if (resultFromGroundTruthForTool.isWorkCorrectly == "true") {
                    if (resultsFromGroundTruth.kind == "pass") {
                        originalResults.add(toolName to setOf())
                    } else {
                        originalResults.add(toolName to p.first.configuration.initialCWEs.toSet())
                    }
                }

                if (resultFromGroundTruthForTool.isWorkCorrectly.equals("true", true) && !isCorrect) {
                    diffPoints++
                }
            }
            if (diffPoints >= 1) {
                saveResult(
                    originalCwes = originalCwes,
                    resultHeader = ResultHeader(
                        analysisResults = analysisResults,
                        originalResults = originalResults,
                        originalFileName = p.first.configuration.originalUri!!,
                        originalFileCWE = originalCwes,
                        mutationDescriptionChain = p.second.map { it.mutationDescription },
                        kind = resultsFromGroundTruth.kind
                    ),
                    originalUri = originalUri,
                    pathToSources = pathToSources,
                    mutatedUri = mutatedUri
                )
            }
        }
    }

    private fun saveResult(
        originalCwes: Set<Int>,
        resultHeader: ResultHeader,
        originalUri: String,
        pathToSources: String,
        mutatedUri: String
    ) {
        val dirToSave =
            "results/CWE-${setOf(originalCwes).joinToString("_")}"
                .takeIf { !DuplicatesDetector.hasDuplicates(it, resultHeader) } ?: "results/duplicates"
        File(dirToSave).let { resultsDirectory ->
            resultsDirectory.exists().ifFalse { resultsDirectory.mkdirs() }
        }
        val originalName = originalUri.substringAfterLast("/").substringBeforeLast('.')
        val extension = originalUri.substringAfterLast('.')
        val text =
            """${resultHeader.convertToString()}
//Program:
${File("$pathToSources/$mutatedUri").readText()}
""".trimIndent()
        File("$dirToSave/${originalName}_${Random.getRandomVariableName(5)}.$extension").writeText(text)
    }
}