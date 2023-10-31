package org.usvm.runner

import java.io.File

fun main(args: Array<String>) {
    require(args.size >= 7) { "Incorrect number of arguments" }
    val mypyDirPath = args[0]
    val socketPort = args[1].toIntOrNull() ?: error("Second argument must be integer")
    val moduleName = args[2]
    val functionName = args[3]
    val timeoutPerRunMs = args[4].toLongOrNull() ?: error("Fifth argument must be integer")
    val timeoutMs = args[5].toLongOrNull() ?: error("Sixth argument must be integer")
    val programRoots = args.drop(6)
    val runner = PythonMachineSocketRunner(
        File(mypyDirPath),
        programRoots.map { File(it) }.toSet(),
        "localhost",
        socketPort
    )
    runner.use {
        it.analyzeFunction(moduleName, functionName, timeoutPerRunMs, timeoutMs)
    }
}