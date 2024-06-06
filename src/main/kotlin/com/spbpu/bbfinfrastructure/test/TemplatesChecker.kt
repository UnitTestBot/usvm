package com.spbpu.bbfinfrastructure.test

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.tools.SemGrep
import com.spbpu.bbfinfrastructure.tools.SpotBugs
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class TemplatesChecker {

    val testFile = File("lib/filteredTestCode/BenchmarkTest00008.java")

    var project = Project.createJavaProjectFromFiles(
        files = listOf(testFile),
        originalFileName = testFile.name,
        originalCWEs = listOf()
    )

    var curFile = project.files.first()


    fun testTemplates(templateName: String, templateBodyIndex: Int) {
        Transformation.checker = MutationChecker(
            listOf(JCompiler()),
            listOf(SemGrep(), SpotBugs()),
            project,
            curFile,
            false,
        )
        Transformation.updateCtx()
        Files.walk(Paths.get("templates"))
            .map { it.toFile() }
            .filter { it.path.endsWith("tmt") && it.path.contains(templateName) }
            .toArray()
            .map { it as File }
            .toList()
            .forEach {
                println("CHECKING TEMPLATES FROM FILE: ${it.name}")
                testTemplate(it.readText(), it.name, templateBodyIndex)
            }
    }

    fun testTemplate(templateText: String, templateName: String, templateBodyIndex: Int) {
        val parsedTemplates = TestTemplatesInserter.parseTemplate(templateText)!!
        for (i in 0 until parsedTemplates.templates.size) {
            if (templateBodyIndex != -1 && i != templateBodyIndex) continue
            project = Project.createJavaProjectFromFiles(
                files = listOf(testFile),
                originalFileName = testFile.name,
                originalCWEs = listOf()
            )
            curFile = project.files.first()
            val inserter = TestTemplatesInserter()
            println("CHECKING $templateName with index $i")
            inserter.testTransform(templateText, i).ifFalse {
                val mostFrequentError = ErrorCollector.compilationErrors.entries.maxByOrNull { it.value }?.key ?: ""
                val mostFrequentCompilationError =
                    ErrorCollector.compilationErrors
                        .filterNot { it.key.contains("find variable") }
                        .maxByOrNull { it.value }?.key ?: ""
                val mostFrequentErrorWithoutParsing =
                    ErrorCollector.compilationErrors
                        .filterNot { it.key.contains("find variable") }
                        .filterNot { it.key.contains("Syntax error") }
                        .maxByOrNull { it.value }?.key ?: ""
                ErrorCollector.errorMap["$templateName $i"] =
                    Triple(mostFrequentError, mostFrequentCompilationError, mostFrequentErrorWithoutParsing)
            }
            ErrorCollector.compilationErrors.clear()
        }
    }

}

fun main(args: Array<String>) {
    CompilerArgs.testMode = true
    TemplatesChecker().testTemplates(
        templateName = args.firstOrNull() ?: "",
        templateBodyIndex = args.lastOrNull()?.toIntOrNull() ?: -1
    )
    if (ErrorCollector.errorMap.isEmpty()) {
        println("ALL TEMPLATES ARE CORRECT!")
    } else {
        println("FAILED TEMPLATES:\n")
        println("-------------------")
        ErrorCollector.errorMap.forEach { (key, value) ->
            val mostFreqError = value.first
            val mostFreqCompError = if (mostFreqError.contains("find variable")) value.second else value.first
            val mostFreqCompErrorWithoutParsing = value.third
            if (mostFreqError == mostFreqCompError) {
                println("$key\nMost frequent error:\n$mostFreqError")
                println("$key\nMost frequent error without parsing and scope:\n$mostFreqCompErrorWithoutParsing")
            } else {
                println("$key\nMost frequent error:\n$mostFreqError\n")
                println("$key\nMost frequent compilation error:\n$mostFreqCompError")
                println("$key\nMost frequent error without parsing and scope:\n$mostFreqCompErrorWithoutParsing")

            }
            println("-------------------")
        }
    }
}