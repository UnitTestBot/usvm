package com.spbpu.bbfinfrastructure.runner

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.apache.commons.exec.*
import java.io.File
import kotlin.system.exitProcess

const val TIMEOUT_SEC = 3600L

//fun makeCommand(args: Array<String>) = "$COMMAND -PprogramArgs=\"${args.joinToString(" ")}\""
fun main(args: Array<String>) {
    val parser = ArgParser("psi-fuzz")

    val pathToOwasp by parser.option(
        ArgType.String,
        shortName = "d",
        description = "Directory for OWASP"
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

    val badTemplatesOnlyMode by parser.option(
        ArgType.Boolean,
        shortName = "b",
        description = "Bad templates only mode"
    ).default(false)

    parser.parse(args)

    if (!isLocal) {
        if (System.getenv("PRIVATE_KEY_PATH") == "null" || System.getenv("PRIVATE_KEY_PASS") == "null") {
            println("Pass PRIVATE_KEY_PATH and PRIVATE_KEY_PASS as environment properties")
            exitProcess(1)
        }
    }

    fun makeCommand(): CommandLine? {
        val javaVersion = System.getenv()["GRADLE_JAVA_HOME"] ?: ""
        val cmdLine =
            if (javaVersion.isEmpty()) {
                CommandLine.parse("gradle runFuzzer")
            } else {
                CommandLine.parse("gradle runFuzzer -Dorg.gradle.java.home=$javaVersion")
            }
        val arg =
            when {
                isLocal && badTemplatesOnlyMode -> "-PprogramArgs=\"-d $pathToOwasp -l -b -n $numOfFilesToCheck -nm $numberOfMutationsPerFile -nf $numberOfMutantsPerFile\""
                isLocal -> "-PprogramArgs=\"-d $pathToOwasp -l -n $numOfFilesToCheck -nm $numberOfMutationsPerFile -nf $numberOfMutantsPerFile\""
                badTemplatesOnlyMode -> "-PprogramArgs=\"-d $pathToOwasp -b -n $numOfFilesToCheck -nm $numberOfMutationsPerFile -nf $numberOfMutantsPerFile\""
                else -> "-PprogramArgs=\"-d $pathToOwasp -n $numOfFilesToCheck -nm $numberOfMutationsPerFile -nf $numberOfMutantsPerFile\""
            }

        cmdLine.addArgument(arg, false)
        cmdLine.addArgument("-PprivateKeyPass=${System.getenv("PRIVATE_KEY_PASS")}")
        cmdLine.addArgument("-PprivateKeyPath=${System.getenv("PRIVATE_KEY_PATH")}")
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
    /*} else {
        while (true) {
            if (handler.hasRe sult()) System.exit(0)
        }
    } */
}

private fun saveStats(timeElapsedInMinutes: Long) {
    val f = File("bugsPerMinute.txt")
    val curText = StringBuilder(f.readText())
    val bugs = curText.split("\n").first().split(": ").last().toInt()
    val newText = """
        Bugs: $bugs
        Time: $timeElapsedInMinutes
        Bugs per minute: ${bugs.toDouble() / timeElapsedInMinutes.toDouble()} 
    """.trimIndent()
    f.writeText(newText)
}