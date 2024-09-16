package com.spbpu.bbfinfrastructure.util.results

import com.spbpu.bbfinfrastructure.project.LANGUAGE

class ResultHeader(
    val analysisResults: MutableList<Pair<String, Set<Int>>>,
    val originalResults: List<Pair<String, Set<Int>>>,
    val originalFileName: String,
    val originalFileCWE: Set<Int>,
    val mutationDescriptionChain: List<String>,
    val usedExtensions: List<String>,
    val kind: String?
) {


    fun convertToString(language: LANGUAGE): String =
        run {
            val commentKey =
                when (language) {
                    LANGUAGE.PYTHON -> "#"
                    else -> "//"
                }
"""$commentKey${originalResults.joinToString(separator = "\n${commentKey}") { "${it.first} original results: ${it.second}" }}
$commentKey-------------
$commentKey${analysisResults.joinToString(separator = "\n${commentKey}") { "${it.first} analysis results: ${it.second}" }}
${commentKey}Original file name: $originalFileName
${commentKey}Original file CWE's: $originalFileCWE  
${commentKey}Original file kind: ${kind ?: "no info"}
${commentKey}Mutation info: ${mutationDescriptionChain.joinToString(" -> ") { it }} 
${commentKey}Used extensions: ${usedExtensions.joinToString(" | ") { it }}
    """.trimIndent()
        }

    companion object {
        fun convertFromString(str: String, language: LANGUAGE): ResultHeader? = try {
            val lines = str.lines()
            val originalResults = mutableListOf<Pair<String, Set<Int>>>()
            val results = mutableListOf<Pair<String, Set<Int>>>()
            var originalFileName = ""
            var originalFileCWE = emptySet<Int>()
            var kind: String? = null
            val mutationChain = mutableListOf<String>()
            val usedExtensions = mutableListOf<String>()
            val commentKey =
                when (language) {
                    LANGUAGE.PYTHON -> "#"
                    else -> "//"
                }
            for (line in lines) {
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts.subList(1, parts.size).joinToString(":").trim()
                    if (key.startsWith(commentKey)) {
                        when {
                            key.contains("original results") -> {
                                val toolName = key.substringBefore(" ").substringAfter(commentKey)
                                val toolResults = Regex("\\[(.*?)\\]").find(value)?.groupValues?.get(1)
                                    ?.split(",")?.filterNot { it.isEmpty() }?.map { it.trim().toInt() }?.toSet() ?: emptySet()
                                originalResults.add(Pair(toolName, toolResults))
                            }
                            key.contains("analysis results") -> {
                                val toolName = key.substringBefore(" ").substringAfter(commentKey)
                                val toolResults = Regex("\\[(.*?)\\]").find(value)?.groupValues?.get(1)
                                    ?.split(",")?.filterNot { it.isEmpty() }?.map { it.trim().toInt() }?.toSet() ?: emptySet()
                                results.add(Pair(toolName, toolResults))
                            }

                            key == "${commentKey}Original file name" -> originalFileName = value
                            key == "${commentKey}Original file CWE's" -> {
                                originalFileCWE = Regex("\\[(.*?)\\]").find(value)?.groupValues?.get(1)
                                    ?.split(",")?.map { it.trim().toInt() }?.toSet() ?: emptySet()
                            }
                            key == "${commentKey}Original file kind" -> kind = value

                            key == "${commentKey}Mutation info" || key == "$commentKey Mutation info" -> {
                                val mutationDescriptions = value.split(" -> ")
                                mutationDescriptions.forEach {
                                    mutationChain.add(it.trim())
                                }
                            }
                            key == "${commentKey}Used extensions" || key == "$commentKey Used extensions" -> {
                                val usedExt = value.split(" | ")
                                usedExt.forEach {
                                    usedExtensions.add(it.trim())
                                }
                            }
                        }
                    }
                }
            }
            ResultHeader(
                analysisResults = results,
                originalResults = originalResults,
                originalFileName = originalFileName,
                originalFileCWE = originalFileCWE,
                mutationDescriptionChain = mutationChain,
                usedExtensions = usedExtensions,
                kind = kind
            )
        } catch (e: Throwable) {
            null
        }
    }

    override fun equals(other: Any?): Boolean =
        weakEquals(other) && originalFileName == (other as ResultHeader).originalFileName


    fun weakEquals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResultHeader

        if (originalFileCWE != other.originalFileCWE) return false
        if (mutationDescriptionChain != other.mutationDescriptionChain) return false
        val intersectedResults = analysisResults.map { it.first to it.second.intersect(originalFileCWE) }.sortedBy { it.first }
        val intersectedOtherResults = other.analysisResults.map { it.first to it.second.intersect(originalFileCWE) }.sortedBy { it.first }
        return intersectedResults == intersectedOtherResults
    }

    override fun hashCode(): Int {
        var result = analysisResults.hashCode()
        result = 31 * result + originalFileName.hashCode()
        result = 31 * result + originalFileCWE.hashCode()
        result = 31 * result + mutationDescriptionChain.hashCode()
        return result
    }
}

