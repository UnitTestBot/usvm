package com.spbpu.bbfinfrastructure.runner

import org.apache.commons.exec.*
import java.io.File

const val COMMAND = "gradle runFuzzer"
const val TIMEOUT_SEC = 3600L
val files = File("lib/filteredTestCode").listFiles().toList().sortedBy { it.name }
var ind = 0

private fun getFileForFuzz(): File {
    if (ind == files.size) {
        ind = 0
    }
    return files[ind++]
}

fun makeCommand() = "$COMMAND --args='${getFileForFuzz().name}'"

fun main(args: Array<String>) {
    var cmdLine = CommandLine.parse(makeCommand())
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
    executor.execute(cmdLine, handler)

    var globalCounter = 0L
    while (true) {
        println("Elapsed: $timeElapsed")
        if (handler.hasResult()) {
            cmdLine = CommandLine.parse(makeCommand())
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
            executor.execute(cmdLine, handler)
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