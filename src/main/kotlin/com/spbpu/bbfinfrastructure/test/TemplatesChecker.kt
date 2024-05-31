package com.spbpu.bbfinfrastructure.test

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.tools.SemGrep
import com.spbpu.bbfinfrastructure.tools.SpotBugs
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
                testTemplate(it.readText(), templateBodyIndex)
            }
    }

    fun testTemplate(templateText: String, templateBodyIndex: Int) {
        project = Project.createJavaProjectFromFiles(
            files = listOf(testFile),
            originalFileName = testFile.name,
            originalCWEs = listOf()
        )
        curFile = project.files.first()
        val inserter = TestTemplatesInserter()
        inserter.testTransform(templateText, templateBodyIndex)
    }

}

fun main(args: Array<String>) {
    TemplatesChecker().testTemplates(
        templateName = args.firstOrNull() ?: "",
        templateBodyIndex = args.lastOrNull()?.toIntOrNull() ?: -1
    )
}