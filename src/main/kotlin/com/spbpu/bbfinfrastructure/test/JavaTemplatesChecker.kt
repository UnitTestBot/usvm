package com.spbpu.bbfinfrastructure.test

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesParser
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class JavaTemplatesChecker {

    val testFile = File("lib/codeForTests/BenchmarkTest00008.java")

    var project = Project.createJavaProjectFromFiles(
        files = listOf(testFile),
        originalFileName = testFile.name,
        originalCWEs = listOf()
    )

    var curFile = project.files.first()


    fun testTemplates(templateName: String, templateBodyIndex: Int) {
        Transformation.checker = MutationChecker(
            listOf(JCompiler()),
            project,
            curFile,
            false,
        )
        Files.walk(Paths.get(FuzzingConf.pathToTemplates))
            .map { it.toFile() }
            .filter { !it.path.contains("helpers") && !it.path.contains("extensions") }
            .filter { it.path.endsWith("tmt") && it.path.contains(templateName) }
            .toArray()
            .map { it as File }
            .toList()
            .forEach {
                println("CHECKING TEMPLATES FROM FILE: ${it.path}")
                testTemplate(it, it.path, templateBodyIndex)
            }
    }

    fun testTemplate(templateFile: File, templateName: String, templateBodyIndex: Int) {
        val parsedTemplates =  try {
            TemplatesParser.parse(templateFile.path)
        } catch (e: Throwable) {
            println("Sorry, can't parse template $templateFile :(")
            return
        }
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
            inserter.testTransform(templateFile.path, i).ifFalse {
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
    FuzzingConf.testMode = true
    System.setProperty("idea.home.path", "lib/bin")
    JavaTemplatesChecker().testTemplates(
        templateName = args.firstOrNull() ?: "sensitivity/",
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