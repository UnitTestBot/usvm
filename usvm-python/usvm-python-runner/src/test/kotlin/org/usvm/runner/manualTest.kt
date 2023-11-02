package org.usvm.runner

import java.io.File

fun main() {
    val basePath = System.getProperty("project.root")
    val layout = TestingLayout(basePath) // StandardLayout(File(basePath, "build/distributions/usvm-python"))
    val mypyDir = File(basePath, "build/samples_build")
    val root = File(basePath, "src/test/resources/samples")
    val config = USVMPythonConfig(
        layout,
        "java",
        mypyDir.canonicalPath,
        setOf(root.canonicalPath)
    )
    val runConfig = USVMPythonRunConfig(
        USVMPythonFunctionConfig(
            "Methods",
            "external_function"
        ),
        10_000,
        3_000
    )
    /*val debugRunner = DebugRunner(config)
    debugRunner.use {
        it.runProcessAndPrintInfo(runConfig)
    }*/
    val receiver = PrintingResultReceiver()
    val runner = PythonSymbolicAnalysisRunnerImpl(config)
    runner.use {
        val start = System.currentTimeMillis()
        it.analyze(runConfig, receiver) { System.currentTimeMillis() - start >= 20_000 }
    }
}