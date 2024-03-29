package com.spbpu.bbfinfrastructure

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.mutator.Mutator
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.JavaTestSuite
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.server.FuzzServerInteract
import com.spbpu.bbfinfrastructure.tools.SemGrep
import com.spbpu.bbfinfrastructure.tools.SpotBugs
import com.spbpu.bbfinfrastructure.util.ScoreCardParser
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val fileName = "BenchmarkTest00005.java"//args.first()
    ScoreCardParser.initCweToFind(fileName.substringBefore(".java"))
    val project = Project.createJavaProjectFromFiles(listOf(File("lib/filteredTestCode/$fileName")))
    println("BEFORE = $project")
    mutate(project, project.files.first())
    println("AFTER = $project")
    exitProcess(0)
//    exitProcess(0)
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
