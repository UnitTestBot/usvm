package com.spbpu.bbfinfrastructure.results

import com.spbpu.bbfinfrastructure.util.results.ResultHeader
import java.io.File
import kotlin.math.abs

object ResultsSorter {

    private val toolsToCoef = mapOf(
        "SpotBugs" to 0.25,
        "CodeQL" to 1.0,
        "SonarQube" to 0.25,
        "Usvm" to 1.0,
        "Semgrep" to 0.25
    )

    fun sortResults(dirToResultsDirectory: String) {
        val dirs = File(dirToResultsDirectory).listFiles().filter { it.isDirectory && it.name != "duplicates" }
        val headerToFile = mutableMapOf<ResultHeader, File>()
        for (dir in dirs) {
            val sorted = dir.listFiles()
                .mapNotNull { f ->
                    val fileText = f.readText()
                    ResultHeader.convertFromString(fileText)?.also {
                        headerToFile[it] = f
                    }
                }
                .filter { it.results.size == 5 }
                .groupBy { calcDiff(it) }
                .toSortedMap()
                .mapValues { sortByLevenshteinDistance(it.value) }
                .toList()
            val newPath = dir.absolutePath.replace(dirToResultsDirectory, "sortedResults/")
            File(newPath).deleteRecursively()
            sorted.forEachIndexed { ind, (_, headers) ->
                val dirPath = "$newPath/$ind"
                File(dirPath).mkdirs()
                headers.forEach { header ->
                    with(headerToFile[header]!!) {
                        File("$dirPath/$name").writeText(readText())
                    }
                }
            }
        }

    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            dp[i][0] = i
        }

        for (j in 0..n) {
            dp[0][j] = j
        }

        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + minOf(
                        dp[i - 1][j],
                        dp[i][j - 1],
                        dp[i - 1][j - 1]
                    )
                }
            }
        }

        return dp[m][n]
    }

    private fun sortByLevenshteinDistance(headers: List<ResultHeader>): List<ResultHeader> {
        if (headers.size <= 1) return headers

        var furthestPoint = headers.first()
        val sorted = mutableListOf(furthestPoint)
        val remaining = headers.toMutableList()
        remaining.remove(furthestPoint)

        while (remaining.isNotEmpty()) {
            var maxDistance = 0
            var maxDistanceIndex = -1
            for ((i, s) in remaining.withIndex()) {
                val distance =
                    sorted.sumOf {
                        levenshteinDistance(
                            it.getMutationDescriptionChainForComparison(),
                            s.getMutationDescriptionChainForComparison()
                        )
                    }
                if (distance > maxDistance) {
                    maxDistance = distance
                    maxDistanceIndex = i
                }
            }

            if (maxDistanceIndex == -1) {
                maxDistanceIndex = 0
            }
            furthestPoint = remaining[maxDistanceIndex]
            val minDistanceToSorted = sorted.minOf {
                levenshteinDistance(
                    it.getMutationDescriptionChainForComparison(),
                    furthestPoint.getMutationDescriptionChainForComparison()
                )
            }
            if (minDistanceToSorted > 10) {
                sorted.add(furthestPoint)
            }
            remaining.removeAt(maxDistanceIndex)
        }

        return sorted
    }

    private fun ResultHeader.getMutationDescriptionChainForComparison() =
        mutationDescriptionChain.joinToString(" ") { it.substringAfter("from templates/") }

    private fun calcDiff(header: ResultHeader): Double {
        val originalCWE = header.originalFileCWE.first()
        val trueTools = header.results.filter { it.second.contains(originalCWE) }
        val falseTools = header.results.filterNot { it.second.contains(originalCWE) }
        val trueToolsK = trueTools.sumOf { toolsToCoef[it.first]!! }
        val falseToolsK = falseTools.sumOf { toolsToCoef[it.first]!! }
        return abs(trueToolsK - falseToolsK)
    }

}