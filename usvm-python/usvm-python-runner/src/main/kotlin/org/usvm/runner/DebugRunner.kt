package org.usvm.runner

class DebugRunner(config: USVMPythonConfig): USVMPythonRunner(config) {
    fun runProcessAndPrintInfo(runConfig: USVMPythonRunConfig) {
        val builder = setupEnvironment(runConfig)
        builder.redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        val process = builder.start()
        process.waitFor()
        println("Exit status: ${process.exitValue()}")
    }
}