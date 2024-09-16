package com.spbpu.bbfinfrastructure.mutator

import com.spbpu.bbfinfrastructure.project.suite.GlobalTestSuite
import com.spbpu.bbfinfrastructure.sarif.MarkupSarif
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.results.ScoreCardParser
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.io.File

class CSharpMutationManager : MutationManager {

    override fun run(
        pathToBenchmark: String,
        pathToBenchmarkToFuzz: String,
        pathToReportsDir: String,
        pathScriptToStartFuzzBenchmark: String,
        pathToVulnomicon: String,
        numOfFilesToCheck: Int,
        isLocal: Boolean
    ) {
        //Init somehow
        val toolsTruthSarif = File("$pathToBenchmarkToFuzz/tools_truth.sarif")
        toolsTruthSarif.exists().ifFalse { error("Can't find tools_truth.sarif in $pathToBenchmark directory") }
        val decodedToolsTruth = Json.decodeFromString<MarkupSarif.Sarif>(toolsTruthSarif.readText())
        val randomMutationTargets =
            decodedToolsTruth.results
                .filter { it.location.physicalLocation.artifactLocation.uri.endsWith(".py") }
                .filter { it.toolsResults.count { it.isWorkCorrectly == "true" } != 1 }
                .shuffled().take(numOfFilesToCheck)
        for (mutationTarget in randomMutationTargets) {
            val pathToTargetFile = "$pathToBenchmark/${mutationTarget.location.physicalLocation.artifactLocation.uri}"
            val file = File(pathToTargetFile)
            //run mutation target
        }
        GlobalTestSuite.pythonTestSuite.flushSuiteAndRun(
            pathToFuzzBenchmark = pathToBenchmarkToFuzz,
            scriptToStartBenchmark = pathScriptToStartFuzzBenchmark,
            pathToVulnomicon = pathToVulnomicon,
            pathToReportsDir = pathToReportsDir,
            isLocal = isLocal,
        )
        ScoreCardParser.parseAndSaveDiff(
            scorecardsDir = "tmp/scorecards",
            pathToSources = FuzzingConf.tmpPath,
            pathToToolsGroundTruthSarif = "$pathToBenchmarkToFuzz/tools_truth.sarif"
        )
    }

    private fun run(
        pathToTargetFile: File,
    ) {

    }
}
