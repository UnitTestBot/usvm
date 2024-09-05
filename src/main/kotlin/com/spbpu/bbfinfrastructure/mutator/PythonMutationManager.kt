package com.spbpu.bbfinfrastructure.mutator

import com.spbpu.bbfinfrastructure.compiler.python.MyPyTypeChecker
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.project.suite.GlobalTestSuite
import com.spbpu.bbfinfrastructure.sarif.MarkupSarif
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.results.ScoreCardParser
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import java.io.File

class PythonMutationManager : MutationManager {

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
            //Remove all /r due to parsing error
            file.writeText(file.readText().replace("\r", "").replace("\t", "    "))
            file.exists().ifFalse {
                println("Cant find file for mutation $pathToTargetFile")
            }
            val project = Project.createPythonProjectFromFiles(
                files = listOf(file),
                originalFileName = file.name,
                originalCWEs = mutationTarget.ruleId.split(',').filter { it.trim().isNotEmpty() }
                    .map { it.trim().substringAfter("CWE-").toInt() },
                region = mutationTarget.location.physicalLocation.region,
                uri = mutationTarget.location.physicalLocation.artifactLocation.uri,
                originalUri = mutationTarget.location.physicalLocation.artifactLocation.uri
            )
            println("Mutation of target ${file.name} started")
            run(project, project.files.first())
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
        project: Project,
        curFile: BBFFile,
    ) {
        Transformation.checker = MutationChecker(
            listOf(MyPyTypeChecker()),
            project,
            curFile,
            false,
        )
        Mutator(project).startMutate()
    }
}
