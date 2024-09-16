package com.spbpu.bbfinfrastructure.results

import com.spbpu.bbfinfrastructure.project.LANGUAGE
import com.spbpu.bbfinfrastructure.util.CweUtil
import com.spbpu.bbfinfrastructure.util.results.ResultHeader
import name.fraser.neil.plaintext.Diff_match_patch
import java.io.File
import kotlin.math.abs

object ResultsSorter {

    private val toolsToCoef = mapOf(
        "SpotBugs" to 0.25,
        "Bearer" to 0.25,
        "CodeQL" to 1.0,
        "SonarQube" to 0.25,
        "Usvm" to 1.0,
        "Semgrep" to 0.75,
        "Insider" to 0.25,
        "ApplicationInspector" to 0.0,
        "Snyk" to 1.0,
        "Bandit" to 0.25
    )

    fun sortResults(dirToResultsDirectory: String, language: LANGUAGE) {
        val dirs = File(dirToResultsDirectory).listFiles().filter { it.isDirectory && it.name != "duplicates" }
        val headerToFile = mutableMapOf<ResultHeader, File>()
        for (dir in dirs) {
            println("HANDLE DIR ${dir.name}")
            val sorted = dir.listFiles()
                .mapNotNull { f ->
                    val fileText = f.readText()
                    ResultHeader.convertFromString(fileText, language)?.also {
                        headerToFile[it] = f
                    }
                }
//                .filter { it.results.size == 4 }
                .groupBy { calcDiff(it) }
                .toSortedMap()
                .mapValues { sortByDiffMatchPatch(it.value) }
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

    private fun diffMatchPatch(a: String, b: String): Double {
        val patch = Diff_match_patch()
        if (a.length + b.length == 0) return Double.MAX_VALUE
        val diffs = patch.diff_main(a, b)
        var sameNum = 0
        var difNum = 0
        for (dif in diffs) {
            when (dif.operation.name) {
                "EQUAL" -> sameNum += dif.text.length
                else -> difNum += dif.text.length
            }
        }
        return if (sameNum == 0) Double.MIN_VALUE else 0.5 - sameNum.toDouble() / (a.length + b.length)
    }

    private fun sortByDiffMatchPatch(headers: List<ResultHeader>): List<ResultHeader> {
        if (headers.size <= 1) return headers

        var furthestPoint = headers.first()
        val sorted = mutableListOf(furthestPoint)
        val remaining = headers.toMutableList()
        remaining.remove(furthestPoint)

        while (remaining.isNotEmpty()) {
            println("REMAINING SIZE = ${remaining.size}")
            var maxDistance = 0.0
            var maxDistanceIndex = -1
            for ((i, s) in remaining.withIndex()) {
                val distance =
                    sorted.sumOf {
                        diffMatchPatch(
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
                diffMatchPatch(
                    it.getMutationDescriptionChainForComparison(),
                    furthestPoint.getMutationDescriptionChainForComparison()
                )
            }
            if (minDistanceToSorted > 0.001) {
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
        val cwes = listOf(originalCWE) + CweUtil.getCweChildrenOf(originalCWE)
        val trueTools = header.analysisResults.filter { it.second.any { cwes.contains(it) } }
        val falseTools = header.analysisResults.filterNot { it.second.any { cwes.contains(it) } }
        val trueToolsK = trueTools.sumOf { toolsToCoef[it.first]!! }
        val falseToolsK = falseTools.sumOf { toolsToCoef[it.first]!! }
        return abs(trueToolsK - falseToolsK)
    }

}