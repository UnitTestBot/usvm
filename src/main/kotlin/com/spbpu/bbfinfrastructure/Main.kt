package com.spbpu.bbfinfrastructure

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.mutator.Mutator
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.results.ResultsSorter
import com.spbpu.bbfinfrastructure.tools.SemGrep
import com.spbpu.bbfinfrastructure.tools.SpotBugs
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import com.spbpu.bbfinfrastructure.util.results.ScoreCardParser
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val numOfFilesToCheck = 100
    val files = File("lib/filteredTestCode/").listFiles()!!.toList().shuffled().take(numOfFilesToCheck)
    for (f in files) {
        val fileName = f.name
        val initialCWEs = ScoreCardParser.initCweToFind(fileName.substringBefore(".java"))
        val project = Project.createJavaProjectFromFiles(
            listOf(File("lib/filteredTestCode/$fileName")),
            fileName,
            initialCWEs?.toList() ?: listOf()
        )
        println("Mutation of ${f.name} started")
        mutate(project, project.files.first())
    }
    GlobalTestSuite.javaTestSuite.flushSuiteOnServer(
        "/home/stepanov/BenchmarkJavaFuzz/src/main/java/org/owasp/benchmark/testcode",
        "/home/stepanov/BenchmarkJavaFuzz/expectedresults-1.2.csv"
    )
    ScoreCardParser.parseAndSaveDiff("tmp/scorecards", CompilerArgs.tmpPath)
    ResultsSorter.sortResults("./results/")
    exitProcess(0)
}

fun mutate(
    project: Project,
    curFile: BBFFile,
) {
    Transformation.checker = MutationChecker(
        listOf(JCompiler()),
        listOf(SemGrep(), SpotBugs()),
        project,
        curFile,
        false,
    )
    Transformation.updateCtx()
    Mutator(project).startMutate()
}
