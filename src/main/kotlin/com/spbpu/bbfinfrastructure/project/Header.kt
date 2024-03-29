package com.spbpu.bbfinfrastructure.project

import kotlin.reflect.KProperty

data class Header(
    val languageSettings: List<String>,
    val withDirectives: List<String>,
    val ignoreBackends: List<String>,
    val targetBackend: String,
    val useExperimental: List<String>,
    val jvmDefault: String,
    val samConversion: String
) {


    companion object {
        fun createHeader(commentSection: String): Header {
            val commentSectionLines = commentSection.lines()
            val language: String by HeaderDelegate(commentSectionLines, Directives.language)
            val languageFeatures =
                if (language.trim().isEmpty()) listOf()
                else language.substringAfter(Directives.language).split(" ")
            val targetBackend: String by HeaderDelegate(commentSectionLines, Directives.targetBackend)
            val withDirectives = commentSectionLines.filter { it.startsWith(Directives.withDirectives) }
            val ignoreBackends = commentSectionLines.filter { it.startsWith(Directives.ignoreBackends) }
            val useExperimental: String by HeaderDelegate(commentSectionLines, Directives.useExperimental)
            val useExperimentalFeatures =
                if (useExperimental.trim().isEmpty()) listOf()
                else useExperimental.substringAfter(Directives.useExperimental).split(" ")
            val jvmDefault: String by HeaderDelegate(commentSectionLines, Directives.jvmDefault)
            val samConversion: String by HeaderDelegate(commentSectionLines, Directives.samConversions)
            return Header(
                languageFeatures,
                withDirectives,
                ignoreBackends,
                targetBackend,
                useExperimentalFeatures,
                jvmDefault,
                samConversion
            )
        }
    }

    fun isWithCoroutines() = withDirectives.any { it.contains("COROUTINES") }

    override fun toString() =
        StringBuilder().apply {
            if (languageSettings.isNotEmpty()) appendLine("${Directives.language}${languageSettings.joinToString(", ")}")
            if (withDirectives.isNotEmpty()) withDirectives.forEach { appendLine(it) }
            if (ignoreBackends.isNotEmpty()) ignoreBackends.forEach { appendLine(it) }
            if (targetBackend.isNotEmpty()) appendLine(targetBackend)
            if (useExperimental.isNotEmpty()) appendLine("${Directives.useExperimental}${useExperimental.joinToString(", ")}")
            if (jvmDefault.isNotEmpty()) appendLine(jvmDefault)
            if (samConversion.isNotEmpty()) appendLine(samConversion)
        }.toString()


}

internal object Directives {
    const val file = "// FILE: "
    const val module = "// MODULE: "
    const val language = "// !LANGUAGE: "
    const val withDirectives = "// WITH_"
    const val ignoreBackends = "// IGNORE_BACKEND"
    const val targetBackend = "// TARGET_BACKEND: "
    const val coroutinesDirective = "// WITH_COROUTINES"
    const val useExperimental = "// !USE_EXPERIMENTAL: "
    const val jvmDefault = "// !JVM_DEFAULT_MODE: "
    const val samConversions = "// SAM_CONVERSIONS: "
}

private class HeaderDelegate(private val sec: List<String>, private val directive: String) {
    operator fun getValue(line: Any?, property: KProperty<*>): String = sec.findDirectiveAndGetValue(directive)
}

private fun List<String>.findDirectiveAndGetValue(directive: String): String =
    this.find { it.startsWith(directive) }
        ?.takeAfterFirst(directive)
        ?: ""

private fun String.takeAfterFirst(s: String): String = this.substring(this.indexOf(s).takeIf { it >= 0 } ?: 0)