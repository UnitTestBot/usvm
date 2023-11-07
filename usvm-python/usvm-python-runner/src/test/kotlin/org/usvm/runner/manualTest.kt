package org.usvm.runner

import org.usvm.runner.venv.extractVenvConfig
import java.io.File

fun main() {
    val basePath = System.getProperty("project.root")
    val layout = TestingLayout(basePath) // StandardLayout(File(basePath, "build/distributions/usvm-python"))
    val mypyDir = File(basePath, "build/samples_build")
    val root = File(basePath, "src/test/resources/samples")
    val venvConfig = extractVenvConfig("/home/tochilinak/sample_venv/bin/python")
    val config = USVMPythonConfig(
        layout,
        "java",
        mypyDir.canonicalPath,
        setOf(root.canonicalPath),
        venvConfig
    )
    val runConfig = USVMPythonRunConfig(
        USVMPythonMethodConfig(
            "Methods",
            "get_info",
            "Point"
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