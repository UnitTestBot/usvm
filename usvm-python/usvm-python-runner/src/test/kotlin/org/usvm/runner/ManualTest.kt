package org.usvm.runner

import mu.KLogging
import org.usvm.python.ps.PyPathSelectorType
import java.io.File

/**
 * Should be run with task `manualTestOfRunner` after building jar of main usvm-python part.
 * */
fun main() {
    run()
}

private val logger = object : KLogging() {}.logger

fun run(useDebugRunner: Boolean = false) {
    val start = System.currentTimeMillis()
    val basePath = System.getProperty("project.root")
    val layout = TestingLayout(basePath)
    val mypyDir = File(basePath, "build/samples_build")
    val root = File(basePath, "src/test/resources/samples")
    val venvConfig = null
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
    if (!useDebugRunner) {
        /**/
        val receiver = PrintingResultReceiver()
        val runner = PythonSymbolicAnalysisRunnerImpl(config)
        runner.use {
            it.analyze(runConfig, receiver) { System.currentTimeMillis() - start >= 20_000 }
        }
        logger.info("Number of executions: ${receiver.cnt}")
    } else {
        val debugRunner = DebugRunner(config)
        debugRunner.use {
            it.runProcessAndPrintInfo(runConfig)
        }
    }
    logger.info("Time: ${System.currentTimeMillis() - start}")
}
