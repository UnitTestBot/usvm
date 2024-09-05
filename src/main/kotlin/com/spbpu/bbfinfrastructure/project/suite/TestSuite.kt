package com.spbpu.bbfinfrastructure.project.suite

import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.sarif.SarifBuilder
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

abstract class TestSuite {

    abstract fun flushSuiteAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        pathToReportsDir: String,
        isLocal: Boolean
    )
    protected abstract fun rename(project: Project, bbfFile: BBFFile, projectIndex: Int)


    val suiteProjects = mutableListOf<Pair<Project, List<MutationInfo>>>()

    fun addProject(project: Project, mutationChain: List<MutationInfo>) {
        val projectIndex = suiteProjects.size
        val mutatedFile = project.files.first()
        rename(project, mutatedFile, projectIndex)
        suiteProjects.add(project to mutationChain)
    }

    fun fixPath(path: String) =
        if (path.contains("~")) {
            path.substringAfter("/")
        } else {
            path
        }

    fun flushAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        pathToReportsDir: String,
        isLocal: Boolean,
        driverName: String
    ) {
        File(FuzzingConf.tmpPath).deleteRecursively()
        File(FuzzingConf.tmpPath).mkdirs()
        val sarifFile = File("${FuzzingConf.tmpPath}/truth.sarif")
        sarifFile.writeText(SarifBuilder().serialize(suiteProjects, driverName))
        val remoteToLocalPaths = mutableMapOf<String, String>()
        for ((project, _) in suiteProjects) {
            val localPaths = project.saveToDir(FuzzingConf.tmpPath)
            localPaths.forEach { localPath ->
                val fileName = localPath.substringAfter(FuzzingConf.tmpPath)
                val mutatedFileUri = project.configuration.mutatedUri ?: error("URI should not be null")
                val pathToBenchmarkHelpers =
                    pathToFuzzBenchmark + "/" + mutatedFileUri.substringBeforeLast('/')
                if (!fileName.contains(project.files.first().name)) {
                    remoteToLocalPaths["${fixPath(pathToBenchmarkHelpers)}/$fileName"] = localPath
                    return@forEach
                }
                val fullSrcPath = "$pathToFuzzBenchmark/$mutatedFileUri"
                val remotePath = fixPath(fullSrcPath)
                remoteToLocalPaths[remotePath] = localPath
            }
        }
        val pathToTruthSarif = "$pathToFuzzBenchmark/truth.sarif"
        remoteToLocalPaths[fixPath(pathToTruthSarif)] = sarifFile.absolutePath
        File("tmp/scorecards/").deleteRecursively()
        File("tmp/scorecards/").mkdirs()
        if (!isLocal) {
            error("Remote mode unsupported, sorry")
        } else {
            with(ProcessBuilder()) {
                remoteToLocalPaths.entries.map {
                    val cmd = "cp ${Paths.get(it.value).absolutePathString()} ${it.key}"
                    command("bash", "-c", cmd).start().waitFor()
                }
                val execCommand =
                    "cd $pathToVulnomicon; rm -rf $pathToReportsDir; $scriptToStartBenchmark"
                command("bash", "-c", execCommand).start().let { process ->
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println(line)
                    }
                    reader.close()
                    process.waitFor()
                }
                val scoreCardsPaths = StringBuilder()
                command("bash", "-c", "find $pathToReportsDir -name \"*.sarif\"").start().let { process ->
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        scoreCardsPaths.appendLine(line)
                    }
                    reader.close()
                    process.waitFor()
                }
                File("tmp/scorecards/").listFiles().forEach { it.delete() }
                val pathToReports = scoreCardsPaths
                    .split("\n")
                    .dropLast(1)
                    .filterNot { it.contains("truth") }
                    .associateWith { "tmp/scorecards/${it.substringAfterLast('/')}" }
                val commandToCpScoreCards = pathToReports.entries.joinToString("; ") { "cp ${it.key} ${it.value}" }
                command("bash", "-c", commandToCpScoreCards).start().waitFor()
                remoteToLocalPaths.keys.forEach { key ->
                    val commandToRm = "rm $key"
                    command("bash", "-c", commandToRm).start().waitFor()
                }
            }
        }
    }

}