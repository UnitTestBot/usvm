package com.spbpu.bbfinfrastructure.util.statistic

import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesParser
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.results.ResultHeader
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object StatsManager {

    private val templates = Files.walk(Paths.get(FuzzingConf.dirToTemplates))
        .toList()
        .map { it.toFile() }
        .filter { it.isFile && !it.path.contains("extensions") && !it.path.contains("helpers") && !it.path.contains("objects") }
        .map { it to TemplatesParser.parse(it.absolutePath) }
        .flatMap { (f, t) -> List(t.templates.size) { index -> Triple(f.path, index, t) } }

    var currentBadTemplatesList: List<Pair<TemplatesParser.Template, String>> =
        if (FuzzingConf.badTemplatesOnlyMode) {
            getBadTemplatesList()
        } else {
            listOf()
        }

    fun updateBadTemplatesList() {
        currentBadTemplatesList = getBadTemplatesList()
    }

    private fun getBadTemplatesList(): List<Pair<TemplatesParser.Template, String>> =
        calc("results").templatesWithoutResults.map { it.first to it.second }

    fun printStats(pathToResults: String = "sortedResults") {
        val (templatesWithoutResults, successFullTemplates) = calc(pathToResults)
        println("TOTAL TEMPLATES: ${templatesWithoutResults.size + successFullTemplates.size}")
        println("SUCCESS TEMPLATES: ${successFullTemplates.size}")
        println(
            "MOST SUCCESSFUL TEMPLATE: ${
                successFullTemplates.entries.maxByOrNull { it.value }.let { "${it?.key} ${it?.value}" }
            }"
        )
        println(
            "SUCCESSFUL TEMPLATES:\n${
                successFullTemplates.entries.sortedByDescending { it.value }
                    .joinToString("\n") { "${it.key}: ${it.value}" }
            }"
        )
        println(
            "TEMPLATES WITHOUT RESULTS (${templatesWithoutResults.size}):\n${
                templatesWithoutResults.joinToString("\n") { it.second }
            }"
        )
    }

    fun printBugsSortedByFeature(pathToResults: String, forFeatures: Collection<String>? = null) {
        calc(pathToResults, printInfo = true, forFeatures)
    }

    private fun calc(
        pathToResults: String,
        printInfo: Boolean = false,
        forFeatures: Collection<String>? = null
    ): TemplatesResults {
        val templatesWithoutResults = mutableListOf<Pair<TemplatesParser.Template, String>>()
        val successFullTemplates = mutableMapOf<String, Int>()
        val results =
            Files.walk(Paths.get(pathToResults))
                .toList()
                .map { it.toFile() }
                .filter { it.isFile }
                .filter { it.path.endsWith("java") }
                .map { it to ResultHeader.convertFromString(it.readText())!! }
        templates.forEach { (templateName, templateIndex, template) ->
            val resultsForFeature =
                results.filter { it.second.mutationDescriptionChain.any { it.contains("$templateName with index $templateIndex") } }
            if (printInfo) {
                if ((forFeatures != null && forFeatures.contains("$templateName $templateIndex")) || forFeatures == null) {
                    println("------------")
                    println("RESULTS FOR FEATURE $templateName $templateIndex:\n${resultsForFeature.joinToString("\n") { it.first.path }}")
                }
            }
            if (resultsForFeature.isEmpty()) {
                templatesWithoutResults.add(template to "$templateName $templateIndex")
            }
            if (resultsForFeature.isNotEmpty()) {
                successFullTemplates["$templateName $templateIndex"] = resultsForFeature.size
            }
        }
        return TemplatesResults(templatesWithoutResults, successFullTemplates)
    }

    fun saveMutationHistory(pathToTemplateFile: String, randomTemplateIndex: Int) {
        val statisticsFile = File("templates_statistic.txt")
        val deserializedStatistic =
            statisticsFile.exists().ifTrue {
                statisticsFile.readText()
                    .split("\n")
                    .filter { it.trim().isNotEmpty() }
                    .associate {
                        it.split(" -> ").let { it.first().trim() to it.last().trim().toInt() }
                    }.toMutableMap()
            } ?: run {
                statisticsFile.createNewFile()
                mutableMapOf()
            }
        val key = "$pathToTemplateFile $randomTemplateIndex"
        if (deserializedStatistic.containsKey(key)) {
            deserializedStatistic[key] = deserializedStatistic[key]!! + 1
        } else {
            deserializedStatistic[key] = 1
        }
        statisticsFile.writeText(
            deserializedStatistic.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
        )
    }

    private class TemplatesResults(
        val templatesWithoutResults: List<Pair<TemplatesParser.Template, String>>,
        val successFullTemplates: Map<String, Int>
    ) {
        operator fun component1(): List<Pair<TemplatesParser.Template, String>> {
            return templatesWithoutResults
        }

        operator fun component2(): Map<String, Int> {
            return successFullTemplates
        }
    }
}