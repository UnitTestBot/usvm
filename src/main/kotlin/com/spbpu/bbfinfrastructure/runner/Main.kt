package com.spbpu.bbfinfrastructure.runner

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.apache.commons.exec.*
import java.io.File
import kotlin.system.exitProcess

const val TIMEOUT_SEC = 3600L
val files = File("lib/filteredTestCode").listFiles().toList().sortedBy { it.name }
var ind = 0

//fun makeCommand(args: Array<String>) = "$COMMAND -PprogramArgs=\"${args.joinToString(" ")}\""
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

    parser.parse(args)

    if (!isLocal) {
        if (System.getenv("PRIVATE_KEY_PATH") == "null" || System.getenv("PRIVATE_KEY_PASS") == "null") {
            println("Pass PRIVATE_KEY_PATH and PRIVATE_KEY_PASS as environment properties")
            exitProcess(1)
        }
    }

    fun makeCommand(): CommandLine? {
        val cmdLine = CommandLine.parse("gradle runFuzzer")
        val arg =
            if (isLocal)
                "-PprogramArgs=\"-d $pathToOwasp -l -n $numOfFilesToCheck\""
            else
                "-PprogramArgs=\"-d $pathToOwasp -n $numOfFilesToCheck\""

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