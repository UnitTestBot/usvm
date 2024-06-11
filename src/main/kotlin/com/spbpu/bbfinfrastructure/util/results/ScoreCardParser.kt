package com.spbpu.bbfinfrastructure.util.results

import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
import com.spbpu.bbfinfrastructure.sarif.MarkupSarif
import com.spbpu.bbfinfrastructure.sarif.ResultSarifBuilder
import com.spbpu.bbfinfrastructure.util.getRandomVariableName
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.io.File
import kotlin.random.Random

object ScoreCardParser {

    private val groundTrue =
        File("lib/expectedresults-1.2.csv")
            .readText()
            .split("\n")
            .drop(1)
            .dropLast(1)
            .associate {
                it.split(",").let { it[0] to setOf(it.last().toInt()) }
            }

//    var cweToFind: Set<Int>? = null
//    var originalFileName: String? = "EMPTY"

    fun initCweToFind(name: String): Set<Int>? {
        return groundTrue[name]
    }

    fun parseAndSaveDiff(dir: String, pathToSources: String) {
        val resultSarifBuilder = ResultSarifBuilder()
        val m = mutableMapOf<String, MutableList<Pair<String, Set<Int>>>>()
        val scorecards = File(dir).listFiles().filter { it.path.endsWith(".sarif") }
        val toolsGroundTruth = Json.decodeFromString<MarkupSarif.Sarif>(File("lib/tools_truth.sarif").readText())
        scorecards.map { scoreCard ->
            val decodedSarif = resultSarifBuilder.deserialize(scoreCard.readText())
            val toolName = scoreCard.name.substringBefore('_')
            val groupedResults = decodedSarif.runs.first().results.groupBy {
                it.locations.first().physicalLocation.artifactLocation.uri
            }
            groupedResults.forEach { (pathToSrc, results) ->
                if (!pathToSrc.matches(Regex(""".*BenchmarkTest.*java"""))) {
                    return@forEach
                }
                val foundCWE = results.mapNotNull { it.ruleId.substringAfter('-').toIntOrNull() }.toSet()
                val benchmarkName = pathToSrc.substringAfterLast("/").substringBefore(".java")
                m.getOrPut(benchmarkName) { mutableListOf() }.add(toolName to foundCWE)
            }
        }
        val toolsNames = scorecards.map { it.name.substringBefore("_") }
        m.forEach { (_, results) ->
            toolsNames.forEach { toolName ->
                if (results.all { it.first != toolName }) {
                    results.add(toolName to setOf())
                }
            }
        }
        for ((name, results) in m) {
            println("Results for $name: ${results.joinToString(" ") { "${it.first} ${it.second}" }}")
            val originalFileName = "BenchmarkTest" + name.substringAfter("BenchmarkTest").take(5)
            val originalProject =
                GlobalTestSuite.javaTestSuite.suiteProjects.find { (project, _) -> project.files.any { it.name == "$name.java" } }
                    ?: error("Cant find original project with name $name")
            val grResult = toolsGroundTruth.results.find { it.location.contains(originalFileName) } ?: continue
            val cweToFind = grResult.ruleId.toInt()
            val kindAsBoolean = grResult.kind == "fail"
            val correctness = results.associate { it.first to (it.second.contains(cweToFind) && kindAsBoolean) }
            val grResultCorrectness = grResult.toolsResults.associate { it.toolName to it.isWorkCorrectly.toBoolean() }
            if (correctness == grResultCorrectness) {
                println("$name: NO DIFFS")
                continue
            }
            if (correctness.values.toSet().size == 1) {
                println("$name: ALL TOOLS WORKS SIMILAR")
                continue
            }
            var diffPoints = 0
            correctness.entries.forEach { (toolName, isCorrect) ->
                val originalCorrectness = grResultCorrectness[toolName] ?: return@forEach
                if (isCorrect != originalCorrectness) {
                    diffPoints++
                }
            }
            if (diffPoints != 0) {
                val groundTruthCWE = grResultCorrectness.entries.map { (toolName, isCorrect) ->
                    val cwes =
                        if (kindAsBoolean && isCorrect) {
                            setOf(cweToFind)
                        } else {
                            setOf()
                        }
                    toolName to cwes
                }
                println("DIFF FOUND!!")
                val resultHeader = ResultHeader(
                    results,
                    groundTruthCWE,
                    originalFileName,
                    setOf(cweToFind),
                    originalProject.second.map { it.mutationDescription }
                )
                val dirToSave =
                    "results/CWE-${setOf(cweToFind).joinToString("_")}"
                        .takeIf { !DuplicatesDetector.hasDuplicates(it, resultHeader) } ?: "results/duplicates"
                File(dirToSave).let { resultsDirectory ->
                    resultsDirectory.exists().ifFalse { resultsDirectory.mkdirs() }
                }
                val text =
                    """${resultHeader.convertToString()}
//Program:
${File("$pathToSources/$name.java").readText()}
""".trimIndent()
                File("$dirToSave/${Random.getRandomVariableName(5)}.java").writeText(text)
            }
        }
    }
}