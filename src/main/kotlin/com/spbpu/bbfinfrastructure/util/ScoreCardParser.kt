package com.spbpu.bbfinfrastructure.util

import java.io.File
import kotlin.random.Random

object ScoreCardParser {

    val groundTrue =
        File("lib/expectedresults-1.2.csv")
            .readText()
            .split("\n")
            .drop(1)
            .dropLast(1)
            .associate {
                it.split(",").let { it[0] to setOf(it.last().toInt()) }
            }

    var cweToFind: Set<Int>? = null
    var originalFileName: String? = "EMPTY"

    fun initCweToFind(name: String) {
        cweToFind = groundTrue[name]
        originalFileName = name
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
        if (cweToFind == null) return
        for ((name, results) in m) {
            println("Results for $name: ${results.joinToString(" ") { "${it.first} ${it.second}" }}")
            val cwes = results.map { it.second }
            val firstBenchRes = cwes.first()
            if (firstBenchRes.isEmpty()) {
                println("ZERO DEFECTS")
                continue
            }
            if (cwes.all { it.intersect(cweToFind!!).isNotEmpty() }) {
                println("ALL TOOLS FOUND BUG IN $name")
            } else if (cwes.all { it.intersect(cweToFind!!).isEmpty() }){
                println("ALL TOOLS CANT FIND BUG IN $name")
            } else {
                println("DIFF FOUND!!")
                val text =
"""//Analysis results: ${results.joinToString(separator = "\n//")}
//Program (original file $originalFileName):
${File("$pathToSources/$name.java").readText()}
""".trimIndent()
                File("results/${Random.getRandomVariableName(5)}.java").writeText(text)
            }
        }
    }
}