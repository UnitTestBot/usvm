package com.spbpu.bbfinfrastructure.util.results

import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
import com.spbpu.bbfinfrastructure.project.ResultSarifBuilder
import com.spbpu.bbfinfrastructure.util.getRandomVariableName
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
            val originalExpectedResults = File("lib/BenchmarkJavaTemplate/expectedresults-1.2.csv").readText()
            val originExpectedResults =
                originalExpectedResults.split("\n").find { it.startsWith(originalFileName) } ?: continue
            val cweToFind = originExpectedResults.substringAfterLast(',').toIntOrNull()?.let { setOf(it) } ?: continue
            val originalProject =
                GlobalTestSuite.javaTestSuite.suiteProjects.find { (project, _) -> project.files.any { it.name == "$name.java" } }
                    ?: error("Cant find original project with name $name")

            val cwes = results.map { it.second }
            val firstBenchRes = cwes.first()
            if (firstBenchRes.isEmpty()) {
                println("ZERO DEFECTS")
                continue
            }
            if (cwes.all { it.intersect(cweToFind).isNotEmpty() }) {
                println("ALL TOOLS FOUND BUG IN $name")
            } else if (cwes.all { it.intersect(cweToFind).isEmpty() }) {
                println("ALL TOOLS CANT FIND BUG IN $name")
            } else {
                println("DIFF FOUND!!")
                val resultHeader = ResultHeader(
                    results,
                    originalFileName,
                    cweToFind,
                    originalProject.second.map { it.mutationDescription })
                val dirToSave =
                    "results/CWE-${cweToFind.joinToString("_")}"
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