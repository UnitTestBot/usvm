package com.spbpu.bbfinfrastructure

import com.spbpu.bbfinfrastructure.mutator.JavaMutationManager
import com.spbpu.bbfinfrastructure.mutator.PythonMutationManager
import com.spbpu.bbfinfrastructure.results.ResultsSorter
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.statistic.StatsManager
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    System.setProperty("idea.home.path", "lib/bin")
    val parser = ArgParser("psi-fuzz")

    val pathToBenchmark by parser.option(
        ArgType.String,
        shortName = "pathToBench",
        description = "Directory for benchmark"
    ).required()

    val pathToBenchmarkFuzz by parser.option(
        ArgType.String,
        shortName = "pathToFuzzBench",
        description = "Directory for benchmark copy for fuzzing"
    ).required()

    val pathToScript by parser.option(
        ArgType.String,
        shortName = "pathToScript",
        description = "Path to script to execute FuzzBenchmark"
    ).required()

    val pathToVulnomicon by parser.option(
        ArgType.String,
        shortName = "pathToVuln",
        description = "Path to vulnomicon"
    ).required()

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

    val badTemplatesOnlyMode by parser.option(
        ArgType.Boolean,
        shortName = "b",
        description = "Bad templates only mode"
    ).default(false)

    val language by parser.option(
        ArgType.String,
        shortName = "l",
        description = "Target programming language"
    ).default("java")

    if (args.size == 1) {
        parser.parse(args.first().split(" ").toTypedArray())
    } else {
        parser.parse(args)
    }

    if (sortResults) {
        ResultsSorter.sortResults("./results/")
        exitProcess(0)
    }

    FuzzingConf.numberOfMutationsPerFile = numberOfMutationsPerFile
    FuzzingConf.numberOfMutantsPerFile = numberOfMutantsPerFile
    FuzzingConf.badTemplatesOnlyMode = badTemplatesOnlyMode
    if (badTemplatesOnlyMode) {
        StatsManager.updateBadTemplatesList()
    }

    val mutationManager =
        when (language) {
            "java" -> JavaMutationManager()
            "python" -> PythonMutationManager()
            else -> error("Not supported language")
        }

    mutationManager.run(
        pathToBenchmark = pathToBenchmark,
        pathToBenchmarkToFuzz = pathToBenchmarkFuzz,
        pathScriptToStartFuzzBenchmark = pathToScript,
        pathToVulnomicon = pathToVulnomicon,
        numOfFilesToCheck = numOfFilesToCheck,
        isLocal = true
    )
}