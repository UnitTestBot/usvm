package org.usvm.runner

import org.usvm.python.ps.PyPathSelectorType
import java.io.File

fun main() {
    val start = System.currentTimeMillis()
    val basePath = System.getProperty("project.root")
    val layout = TestingLayout(basePath) // StandardLayout(File(basePath, "build/distributions/usvm-python"))
    val mypyDir = File(basePath, "build/samples_build")
    val root = File(basePath, "src/test/resources/samples")
    val venvConfig = null // extractVenvConfig("/home/tochilinak/sample_venv/bin/python")
    val config = USVMPythonConfig(
        layout,
        "java",
        mypyDir.canonicalPath,
        setOf(root.canonicalPath),
        venvConfig,
        PyPathSelectorType.BaselinePriorityDfs
    )
    val runConfig = USVMPythonRunConfig(
        USVMPythonFunctionConfig(
            "tricky.CompositeObjects",
            "g"
        ),
        20_000,
        3_000
    )
    /*val debugRunner = DebugRunner(config)
    debugRunner.use {
        it.runProcessAndPrintInfo(runConfig)
    }*/
    val receiver = PrintingResultReceiver()
    val runner = PythonSymbolicAnalysisRunnerImpl(config)
    runner.use {
        it.analyze(runConfig, receiver) { System.currentTimeMillis() - start >= 20_000 }
    }
    println("Time: ${System.currentTimeMillis() - start}")
    println("Number of executions: ${receiver.cnt}")
}