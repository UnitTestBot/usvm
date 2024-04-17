package com.spbpu.bbfinfrastructure.util.results

import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
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
    val duplicatesFilter = DuplicatesFilter("results")

//    var cweToFind: Set<Int>? = null
//    var originalFileName: String? = "EMPTY"

    fun initCweToFind(name: String): Set<Int>? {
        return groundTrue[name]
    }

    fun parseAndSaveDiff(dir: String, pathToSources: String) {
        val m = mutableMapOf<String, MutableList<Pair<String, Set<Int>>>>()
        val scorecards = File(dir).listFiles().filter { it.path.endsWith(".csv") }
        scorecards.map { scoreCard ->
            val toolName = scoreCard.name.substringAfter("for_").substringBefore("_")
            val scoreCardText = scoreCard.readText()
            val scLines = scoreCardText.split("\n").drop(1).dropLast(1)
            for (line in scLines) {
                Regex("""(.*), unknown, 0, unknown, unknown, null, "\[(.*)\]"""").find(line)!!.groupValues.let {
                    val cwes = if (it[2].isEmpty()) {
                        listOf()
                    } else {
                        it[2].split(", ").map { it.trim().toInt() }
                    }
                    val benchmarkName = it[1]
                    m.getOrPut(benchmarkName) { mutableListOf() }.add(toolName to cwes.toSet())
                }
            }
        }

        for ((name, results) in m) {
            println("Results for $name: ${results.joinToString(" ") { "${it.first} ${it.second}" }}")
            val originalProject =
                GlobalTestSuite.javaTestSuite.suiteProjects.find { (project, _) -> project.files.any { it.name == "$name.java" } }
                    ?: error("Cant find original project with name $name")
            val originalFileName = "BenchmarkTest" + name.substringAfter("BenchmarkTest").take(5)
            val originalExpectedResults = File("lib/BenchmarkJavaTemplate/expectedresults-1.2.csv").readText()
            val originExpectedResults =
                originalExpectedResults.split("\n").find { it.startsWith(originalFileName) } ?: continue
            val cweToFind = originExpectedResults.substringAfterLast(',').toIntOrNull()?.let { setOf(it) } ?: continue
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
                val resultHeader = ResultHeader(results, originalFileName, cweToFind, originalProject.second.map { it.mutationDescription })
                val dirToSave =
                    if (duplicatesFilter.hasDuplicates(resultHeader)) {
                        "results/duplicates"
                    } else {
                        "results"
                    }
                File(dirToSave).let { resultsDirectory -> resultsDirectory.exists().ifFalse { resultsDirectory.mkdirs() }}
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