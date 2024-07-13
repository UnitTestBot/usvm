package org.usvm.runner

import mu.KLogging

private val logger =  object : KLogging() {}.logger

class DebugRunner(config: USVMPythonConfig) : USVMPythonRunner(config) {
    fun runProcessAndPrintInfo(runConfig: USVMPythonRunConfig) {
        val builder = setupEnvironment(runConfig)
        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        val process = builder.start()
        process.waitFor()
        when (val status = process.exitValue()) {
            0 -> logger.info("Exit status: 0 (Success)")
            else -> logger.info("Exit status: $status (Failure)")
        }
    }
}
