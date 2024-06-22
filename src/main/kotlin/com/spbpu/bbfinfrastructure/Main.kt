package com.spbpu.bbfinfrastructure

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.markup.MarkupBenchmark
import com.spbpu.bbfinfrastructure.mutator.Mutator
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.results.ResultsSorter
import com.spbpu.bbfinfrastructure.util.*
import com.spbpu.bbfinfrastructure.util.results.ScoreCardParser
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    System.setProperty("idea.home.path", "lib/bin")
    val parser = ArgParser("psi-fuzz")

    val pathToBenchmark by parser.option(
        ArgType.String,
        shortName = "d",
        description = "Directory for benchmark"
    ).default("~/vulnomicon/BenchmarkJava-mutated")

    val isLocal by parser.option(
        ArgType.Boolean,
        shortName = "l",
        description = "Indicates if the fuzzing process is local"
    ).default(false)

    val numOfFilesToCheck by parser.option(
        ArgType.Int,
        shortName = "n",
        description = "Number of files to make a batch"
    ).default(500)

    val numberOfMutationsPerFile by parser.option(
        ArgType.Int,
        shortName = "nm",
        description = "Number of successful mutations to make final version of mutant"
    ).default(2)

    val numberOfMutantsPerFile by parser.option(
        ArgType.Int,
        shortName = "nf",
        description = "Number of generated mutants for file"
    ).default(5)

    val sortResults by parser.option(
        ArgType.Boolean,
        shortName = "s",
        description = "Choose this flag if you want to sort results (may be slow)"
    ).default(false)

    val markupBenchmark by parser.option(
        ArgType.Boolean,
        shortName = "m",
        description = "Markup benchmark"
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

    if (markupBenchmark) {
        MarkupBenchmark().markup(
            pathToGroundTruth = "lib/truth.sarif",
            pathToSrc = "lib/filteredTestCode",
            toolsResultsPaths = listOf(
                "lib/CodeQL_Default.sarif",
                "lib/Insider_Default.sarif",
                "lib/Semgrep_Default.sarif",
                "lib/SonarQube_Default.sarif"
            ),
            pathToResultSarif = "lib/tools_truth.sarif"
        )
        exitProcess(0)
    }

    if (!isLocal) {
        if (System.getenv("PRIVATE_KEY_PATH") == "null" || System.getenv("PRIVATE_KEY_PASS") == "null") {
            println("Pass PRIVATE_KEY_PATH and PRIVATE_KEY_PASS as environment properties")
            exitProcess(1)
        }
    }

    CompilerArgs.numberOfMutationsPerFile = numberOfMutationsPerFile
    CompilerArgs.numberOfMutantsPerFile = numberOfMutantsPerFile
    val owaspFiles = File("lib/filteredTestCode/").listFiles().toList()
    val julietFiles = File("lib/juliet/testcode").listFiles().toList()
//    val files = (owaspFiles + julietFiles).shuffled().take(numOfFilesToCheck)
//    val isOwasp = Random.nextBoolean()
    val isOwasp = true
    val files =
        if (isOwasp) {
            owaspFiles.shuffled().take(numOfFilesToCheck)
        } else {
            julietFiles.shuffled().take(numOfFilesToCheck)
        }
//    val files = julietFiles.shuffled().take(1)
    for (f in files) {
        val fileName = f.name
        val project = Project.createJavaProjectFromFiles(
            listOf(f),
            fileName,
            listOf()
        )
        println("Mutation of ${f.name} started")
        mutate(project, project.files.first())
    }

    val pp =
        if (isOwasp) {
            "~/vulnomicon/BenchmarkJava-mutated"
        } else {
            "~/vulnomicon/JulietJavaForMutation"
        }
    if (isOwasp) {
        GlobalTestSuite.javaTestSuite.flushSuiteAndRun(
            pathToBenchmark = pp,
            pathToBenchmarkSources = "$pp/src/main/java/org/owasp/benchmark/testcode",
            pathToBenchmarkHelpers = "$pp/src/main/java/org/owasp/benchmark/helpers",
            pathToTruthSarif = "$pp/truth.sarif",
            scriptToStartBenchmark = "./scripts/runBenchmarkJavaMutated.sh",
            isLocal = isLocal
        )
        ScoreCardParser.parseAndSaveDiff(
            scorecardsDir = "tmp/scorecards",
            pathToSources = CompilerArgs.tmpPath,
            nameMask = Regex("""BenchmarkTest\d\d\d\d\d"""),
            pathToToolsGroundTruthSarif = "lib/tools_truth.sarif"
        )
    } else {
        GlobalTestSuite.javaTestSuite.flushSuiteAndRun(
            pathToBenchmark = pp,
            pathToBenchmarkSources = "$pp/src/main/java/juliet/testcases",
            pathToBenchmarkHelpers = "$pp/src/main/java/juliet/support",
            pathToTruthSarif = "$pp/truth.sarif",
            scriptToStartBenchmark = "./scripts/runJulietBenchmark.sh",
            isLocal = isLocal
        )
        ScoreCardParser.parseAndSaveDiff(
            scorecardsDir = "tmp/scorecards",
            pathToSources = CompilerArgs.tmpPath,
            nameMask = Regex("""CWE.*(good|bad)"""),
            pathToToolsGroundTruthSarif = "lib/juliet/tools_truth.sarif"
        )
    }
    exitProcess(0)
}

fun mutate(
    project: Project,
    curFile: BBFFile,
) {
    Transformation.checker = MutationChecker(
        listOf(JCompiler()),
        project,
        curFile,
        false,
    )
    Mutator(project).startMutate()
}
