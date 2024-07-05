package com.spbpu.bbfinfrastructure.markup

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("psi-fuzz-markup")

    val pathToBenchmarkFuzz by parser.option(
        ArgType.String,
        shortName = "pathToFuzzBench",
        description = "Directory for benchmark copy for fuzzing"
    ).required()

    val pathToToolsResultsDirectory by parser.option(
        ArgType.String,
        shortName = "tools",
        description = "Path to tools results directory"
    ).required()

    parser.parse(args)

    val toolsResults = File(pathToToolsResultsDirectory).listFiles()
        .filter { it.path.endsWith(".sarif") }
        .filterNot { it.path.contains("truth.sarif") }
        .map { it.absolutePath }
        .ifEmpty { error("Cannot find sarif files in specified directory $pathToToolsResultsDirectory") }

    val pathToTruthSarif = "$pathToBenchmarkFuzz/truth.sarif"
    if (!File(pathToTruthSarif).exists()) {
        error("Cannot find truth sarif $pathToTruthSarif")
    }

    val pathToResultSarif =
        if (pathToTruthSarif.contains("/")) {
            pathToTruthSarif.substringBeforeLast("/") + "/tools_truth.sarif"
        } else {
            "./tools_truth.sarif"
        }

    MarkupBenchmark().markup(
        pathToTruthSarif,
        toolsResults,
        pathToResultSarif
    )
}