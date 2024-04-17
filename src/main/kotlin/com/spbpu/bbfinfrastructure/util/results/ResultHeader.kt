package com.spbpu.bbfinfrastructure.util.results

class ResultHeader(
    val results: MutableList<Pair<String, Set<Int>>>,
    val originalFileName: String,
    val originalFileCWE: Set<Int>,
    val mutationDescriptionChain: List<String>
) {


    fun convertToString(): String =
"""//Analysis results:
//${results.joinToString(separator = "\n//") { "Tool name: ${it.first} Results: ${it.second}" }}
//Original file name: $originalFileName
//Original file CWE's: $originalFileCWE  
//Mutation info: ${mutationDescriptionChain.joinToString(" -> ") { it }} 
    """.trimIndent()

    companion object {
        fun convertFromString(str: String): ResultHeader? = try {
            val lines = str.lines()
            val results = mutableListOf<Pair<String, Set<Int>>>()
            var originalFileName = ""
            var originalFileCWE = emptySet<Int>()
            val mutationChain = mutableListOf<String>()

            for (line in lines) {
                val parts = line.split(":")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = parts.subList(1, parts.size).joinToString(":").trim()
                    if (key.startsWith("//")) {
                        when {
                            key.startsWith("//Tool name") -> {
                                val toolName = value.split(" ")[0]
                                val toolResults = Regex("\\[(.*?)\\]").find(value)?.groupValues?.get(1)
                                    ?.split(",")?.filterNot { it.isEmpty() }?.map { it.trim().toInt() }?.toSet() ?: emptySet()
                                results.add(Pair(toolName, toolResults))
                            }

                            key == "//Original file name" -> originalFileName = value
                            key == "//Original file CWE's" -> {
                                originalFileCWE = Regex("\\[(.*?)\\]").find(value)?.groupValues?.get(1)
                                    ?.split(",")?.map { it.trim().toInt() }?.toSet() ?: emptySet()
                            }

                            key == "//Mutation info" -> {
                                val mutationDescriptions = value.split(" -> ")
                                mutationDescriptions.forEach {
                                    mutationChain.add(it.trim())
                                }
                            }
                        }
                    }
                }
            }
            ResultHeader(results, originalFileName, originalFileCWE, mutationChain)
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
        val intersectedResults = results.map { it.first to it.second.intersect(originalFileCWE) }.sortedBy { it.first }
        val intersectedOtherResults = other.results.map { it.first to it.second.intersect(originalFileCWE) }.sortedBy { it.first }
        return intersectedResults == intersectedOtherResults
    }

    override fun hashCode(): Int {
        var result = results.hashCode()
        result = 31 * result + originalFileName.hashCode()
        result = 31 * result + originalFileCWE.hashCode()
        result = 31 * result + mutationDescriptionChain.hashCode()
        return result
    }
}

