package com.spbpu.bbfinfrastructure.mutator

import com.spbpu.bbfinfrastructure.project.suite.PythonTestSuite
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import com.spbpu.bbfinfrastructure.util.results.ScoreCardParser

class PythonMutationManager: MutationManager {

    override fun run(
        pathToBenchmark: String,
        pathToBenchmarkToFuzz: String,
        pathScriptToStartFuzzBenchmark: String,
        pathToVulnomicon: String,
        numOfFilesToCheck: Int,
        isLocal: Boolean
    ) {
        //Init somehow
        val pythonTestSuite = PythonTestSuite()
        pythonTestSuite.flushSuiteAndRun(
            pathToFuzzBenchmark = pathToBenchmarkToFuzz,
            scriptToStartBenchmark = pathScriptToStartFuzzBenchmark,
            pathToVulnomicon = pathToVulnomicon,
            isLocal = isLocal,
        )
        ScoreCardParser.parseAndSaveDiff(
            scorecardsDir = "tmp/scorecards",
            pathToSources = CompilerArgs.tmpPath,
            pathToToolsGroundTruthSarif = "$pathToBenchmarkToFuzz/tools_truth.sarif"
        )
        TODO("Not yet implemented")
    }
}
