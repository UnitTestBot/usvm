package org.usvm.runner

import mu.KLogging
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.venv.VenvConfig
import org.usvm.python.ps.PyPathSelectorType
import java.io.File

/**
 * This is supposed to be called only from `usvm-python-runner`.
 * Not designed for human usage.
 * */

private const val MYPY_DIR_ARG = 0
private const val SOCKET_PORT_ARG = 1
private const val MODULE_NAME_ARG = 2
private const val FUNCTION_NAME_ARG = 3
private const val CLASS_NAME_ARG = 4
private const val TIMEOUT_PER_RUN_ARG = 5
private const val TIMEOUT_ARG = 6
private const val PATH_SELECTOR_ARG = 7
private const val VENV_ARG = 8
private const val MIN_PREFIX_LENGTH = 9
private const val LIB_ARG = 9
private const val BIN_ARG = 10

private val logger = object : KLogging() {}.logger

fun main(args: Array<String>) {
    var prefixNumberOfArgs = MIN_PREFIX_LENGTH
    require(args.size >= prefixNumberOfArgs + 1) { "Incorrect number of arguments" }
    val mypyDirPath = args[MYPY_DIR_ARG]
    val socketPort = args[SOCKET_PORT_ARG].toIntOrNull() ?: error("Second argument must be integer")
    val moduleName = args[MODULE_NAME_ARG]
    val functionName = args[FUNCTION_NAME_ARG]
    val clsName = if (args[CLASS_NAME_ARG] == "<no_class>") null else args[CLASS_NAME_ARG]
    val timeoutPerRunMs = args[TIMEOUT_PER_RUN_ARG].toLongOrNull() ?: error("Sixth argument must be integer")
    val timeoutMs = args[TIMEOUT_ARG].toLongOrNull() ?: error("Seventh argument must be integer")
    val pathSelectorName = args[PATH_SELECTOR_ARG]
    val pathSelector = PyPathSelectorType.valueOf(pathSelectorName)
    if (args[VENV_ARG] != "<no_venv>") {
        prefixNumberOfArgs += 2
        require(args.size >= prefixNumberOfArgs + 1) { "Incorrect number of arguments" }
        val venvConfig = VenvConfig(
            basePath = File(args[VENV_ARG]),
            libPath = File(args[LIB_ARG]),
            binPath = File(args[BIN_ARG])
        )
        ConcretePythonInterpreter.setVenv(venvConfig)
        logger.info { "VenvConfig: $venvConfig" }
    } else {
        logger.warn("No VenvConfig.")
    }
    val programRoots = args.drop(prefixNumberOfArgs)
    val runner = PyMachineSocketRunner(
        File(mypyDirPath),
        programRoots.map { File(it) }.toSet(),
        "localhost",
        socketPort,
        pathSelector
    )
    runner.use {
        if (clsName == null) {
            it.analyzeFunction(moduleName, functionName, timeoutPerRunMs, timeoutMs)
        } else {
            it.analyzeMethod(moduleName, functionName, clsName, timeoutPerRunMs, timeoutMs)
        }
    }
}
