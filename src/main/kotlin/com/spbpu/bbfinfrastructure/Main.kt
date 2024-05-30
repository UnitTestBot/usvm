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
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val parser = ArgParser("psi-fuzz")

    val pathToOwasp by parser.option(
        ArgType.String,
        shortName = "d",
        description = "Directory for OWASP"
    ).required()

    val isLocal by parser.option(
        ArgType.Boolean,
        shortName = "l",
        description = "Indicates if the fuzzing process is local"
    ).default(false)

    val numOfFilesToCheck by parser.option(
        ArgType.Int,
        shortName = "n",
        description = "Number of files to make a batch"
    ).default(100)

    val sortResults by parser.option(
        ArgType.Boolean,
        shortName = "s",
        description = "Choose this flag if you want to sort results (may be slow)"
    ).default(false)

    if (args.size == 1) {
        parser.parse(args.first().split(" ").toTypedArray())
    } else {
        parser.parse(args)
    }

    if (sortResults) {
        ResultsSorter.sortResults("./results/")
        exitProcess(0)
    }

    if (!isLocal) {
        if (System.getenv("PRIVATE_KEY_PATH") == "null" || System.getenv("PRIVATE_KEY_PASS") == "null") {
            println("Pass PRIVATE_KEY_PATH and PRIVATE_KEY_PASS as environment properties")
            exitProcess(1)
        }
    }

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
    GlobalTestSuite.javaTestSuite.flushSuiteAndRun(
        pathToOwasp = pathToOwasp,
        pathToOwaspSources = "$pathToOwasp/src/main/java/org/owasp/benchmark/testcode",
        pathToTruthSarif = "$pathToOwasp/truth.sarif",
        isLocal = isLocal
    )
    ScoreCardParser.parseAndSaveDiff("tmp/scorecards", CompilerArgs.tmpPath)
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
