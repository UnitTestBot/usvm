package com.spbpu.bbfinfrastructure.runner

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.apache.commons.exec.*
import java.io.File
import kotlin.system.exitProcess

const val TIMEOUT_SEC = 3600L

fun main(args: Array<String>) {
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

    parser.parse(args)

    fun makeCommand(): CommandLine? {
        val javaVersion = System.getenv()["GRADLE_JAVA_HOME"] ?: ""
        val vulnomiconJava = System.getenv()["VULNOMICON_JAVA_HOME_17"] ?: ""
        val cmdLine =
            if (javaVersion.isEmpty()) {
                CommandLine.parse("gradle runFuzzer")
            } else {
                CommandLine.parse("gradle runFuzzer -Dorg.gradle.java.home=$javaVersion -PvulnomiconJavaHome=$vulnomiconJava")
            }
        val arg =
            when {
                badTemplatesOnlyMode ->
                    "-PprogramArgs=\"-pathToBench $pathToBenchmark " +
                        "-pathToFuzzBench $pathToBenchmarkFuzz " +
                        "-pathToScript $pathToScript " +
                        "-pathToVuln $pathToVulnomicon " +
                        "-b " +
                        "-n $numOfFilesToCheck " +
                        "-nm $numberOfMutationsPerFile " +
                        "-nf $numberOfMutantsPerFile\""
                else ->
                    "-PprogramArgs=\"-pathToBench $pathToBenchmark " +
                            "-pathToFuzzBench $pathToBenchmarkFuzz " +
                            "-pathToScript $pathToScript " +
                            "-pathToVuln $pathToVulnomicon " +
                            "-n $numOfFilesToCheck " +
                            "-nm $numberOfMutationsPerFile " +
                            "-nf $numberOfMutantsPerFile\""
            }

        cmdLine.addArgument(arg, false)
        return cmdLine
    }

    var executor = DefaultExecutor().also {
        it.watchdog = ExecuteWatchdog(TIMEOUT_SEC * 1000)
        it.streamHandler = PumpStreamHandler(object : LogOutputStream() {
            override fun processLine(line: String?, level: Int) {
                println(line)
            }
        })
    }
    var handler = DefaultExecuteResultHandler()
    var timeElapsed = 0
    executor.execute(makeCommand(), handler)

    var globalCounter = 0L
    while (true) {
        println("Elapsed: $timeElapsed")
        if (handler.hasResult()) {
            handler = DefaultExecuteResultHandler()
            executor = DefaultExecutor().also {
                it.watchdog = ExecuteWatchdog(TIMEOUT_SEC * 1000)
                it.streamHandler = PumpStreamHandler(object : LogOutputStream() {
                    override fun processLine(line: String?, level: Int) {
                        println(line)
                        //executorOutput.add(line)
                    }
                })
            }
            executor.execute(makeCommand(), handler)
            timeElapsed = 0
        }
        globalCounter += 1000
        timeElapsed += 1000
        Thread.sleep(1000)
    }
}