package org.usvm.runner

import java.io.File

fun main() {
    val start = System.currentTimeMillis()
    val basePath = System.getProperty("project.root")
    val layout = TestingLayout(basePath) // StandardLayout(File(basePath, "build/distributions/usvm-python"))
    val mypyDir = File("/home/tochilinak/Documents/projects/utbot/mypy_tmp")
    val root = File("/home/tochilinak/Documents/projects/utbot/Python/graphs")
    val venvConfig = null // extractVenvConfig("/home/tochilinak/sample_venv/bin/python")
    val config = USVMPythonConfig(
        layout,
        "java",
        mypyDir.canonicalPath,
        setOf(root.canonicalPath),
        venvConfig
    )
    val runConfig = USVMPythonRunConfig(
        USVMPythonMethodConfig(
            "boruvka",
            "boruvka",
            "Graph"
        ),
        40_000,
        4_000
    )
    val debugRunner = DebugRunner(config)
    debugRunner.use {
        it.runProcessAndPrintInfo(runConfig)
    }
    /*val receiver = PrintingResultReceiver()
    val runner = PythonSymbolicAnalysisRunnerImpl(config)
    runner.use {
        it.analyze(runConfig, receiver) { System.currentTimeMillis() - start >= 40_000 }
    }*/
    println("Time: ${System.currentTimeMillis() - start}")
}