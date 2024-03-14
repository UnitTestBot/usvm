package org.usvm.runner

class DebugRunner(config: USVMPythonConfig) : USVMPythonRunner(config) {
    fun runProcessAndPrintInfo(runConfig: USVMPythonRunConfig) {
        val builder = setupEnvironment(runConfig)
        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        val process = builder.start()
        process.waitFor()
        when (val status = process.exitValue()) {
            0 -> println("Exit status: 0 (Success)")
            else -> println("Exit status: $status (Failure)")
        }
    }
}
