package com.spbpu.bbfinfrastructure.markup

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File

fun main() {
    val parser = ArgParser("psi-fuzz")

    val pathToBenchmark by parser.option(
        ArgType.String,
        shortName = "path",
        description = "Directory for benchmark"
    ).required()

    val pathToTruthSarif by parser.option(
        ArgType.String,
        shortName = "sarif",
        description = "Directory for truth.sarif"
    ).required()

    val pathToToolsResultsDirectory by parser.option(
        ArgType.String,
        shortName = "tools",
        description = "Path to tools results directory"
    ).required()

    val toolsResults = File(pathToToolsResultsDirectory).listFiles()
        .filter { it.path.endsWith(".sarif") }
        .filterNot { it.path.contains("truth.sarif") }
        .map { it.absolutePath }
        .ifEmpty { error("Cannot find sarif files in specified directory $pathToToolsResultsDirectory") }

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