package org.usvm.runner.venv

import mu.KLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private val logger = object : KLogging() {}.logger

fun extractVenvConfig(pythonPath: String): VenvConfig? {
    val basePrefix = runPythonAndReadline(
        pythonPath,
        "import sys; print(sys.base_prefix)"
    )
    val prefix = runPythonAndReadline(
        pythonPath,
        "import sys; print(sys.prefix)"
    )
    if (basePrefix == prefix) {
        logger.warn("Given Python is not run inside venv. Setting VenvConfig to null")
        return null
    }
    val version = runPythonAndReadline(
        pythonPath,
        "import sys; print(sys.version_info.major, sys.version_info.minor, sep='.')"
    )
    if (version.trim() != "3.11") {
        logger.warn("Given Python's version is not 3.11. Setting VenvConfig to null")
        return null
    }
    val basePath = File(prefix)
    require(basePath.exists())
    val assumedBinPath = File(basePath, "bin")
    if (!assumedBinPath.exists()) {
        logger.warn("Did not find venv's bin path. Setting VenvConfig to null")
        return null
    }
    val assumedLibPath = File(basePath, "lib/python3.11/site-packages")
    if (!assumedLibPath.exists()) {
        logger.warn("Did not find venv's site-packages path. Setting VenvConfig to null")
        return null
    }
    return VenvConfig(basePath = basePath, binPath = assumedBinPath, libPath = assumedLibPath)
}

private fun runPythonAndReadline(pythonPath: String, cmd: String): String {
    val process = ProcessBuilder(pythonPath, "-c", cmd).start()
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    val result = reader.readLine()
    process.waitFor()
    require(process.exitValue() == 0) { "Something went wrong in Python run" }
    return result
}
