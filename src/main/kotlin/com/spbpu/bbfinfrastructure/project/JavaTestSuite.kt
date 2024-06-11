package com.spbpu.bbfinfrastructure.project

import com.intellij.psi.PsiClass
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.sarif.SarifBuilder
import com.spbpu.bbfinfrastructure.server.FuzzServerInteract
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.replaceThis
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

class JavaTestSuite {

    val suiteProjects = mutableListOf<Pair<Project, List<MutationInfo>>>()

    fun addProject(project: Project, mutationChain: List<MutationInfo> , shouldCheck: Boolean = true) {
        val projectIndex = suiteProjects.size
        project.files.forEach { bbfFile ->
            val psiFile = bbfFile.psiFile
            val name = bbfFile.name
            if (!name.contains("Benchmark")) {
                return@forEach
            }
            val classes =
                psiFile
                    .getAllPSIChildrenOfType<PsiClass>()
                    .filter { it.name?.contains("BenchmarkTest") ?: false }
            for (cl in classes) {
                cl.nameIdentifier?.let {
                    val newIdentifier = PsiIdentifierImpl("${it.text}$projectIndex")
                    it.replaceThis(newIdentifier)
                }
            }
            bbfFile.name = "${name.substringBefore(".java")}$projectIndex.java"
        }
        suiteProjects.add(project to mutationChain)
    }

    fun flushSuiteAndRun(pathToOwasp: String, pathToOwaspSources: String, pathToTruthSarif: String, isLocal: Boolean) {
        val sarifBuilder = SarifBuilder()
        val remoteToLocalPaths = mutableMapOf<String, String>()
        val helpersDir = pathToOwaspSources.substringBeforeLast('/') + "/helpers/"
        File(CompilerArgs.tmpPath).deleteRecursively()
        File(CompilerArgs.tmpPath).mkdirs()
        val sarif = File("${CompilerArgs.tmpPath}/truth.sarif").apply {
            writeText(sarifBuilder.serialize(suiteProjects))
        }
        for ((project, _) in suiteProjects) {
            val localPaths = project.saveToDir(CompilerArgs.tmpPath)
            localPaths.forEach { localPath ->
                val fileName = localPath.substringAfter(CompilerArgs.tmpPath)
                if (!fileName.contains("Benchmark")) {
                    remoteToLocalPaths["$helpersDir/$fileName"] = localPath
                    return@forEach
                }
                val remotePath = "$pathToOwaspSources/$fileName"
                remoteToLocalPaths[remotePath] = localPath
            }
        }
        remoteToLocalPaths[pathToTruthSarif] = sarif.absolutePath
        val cmdToRm =
            remoteToLocalPaths.filterNot { it.key.contains("BenchmarkTest") }.keys.joinToString(" ") { "rm $it;" }
        File("tmp/scorecards/").deleteRecursively()
        File("tmp/scorecards/").mkdirs()
        if (!isLocal) {
            val fsi = FuzzServerInteract()
            fsi.execCommand(cmdToRm)
            fsi.execCommand("rm -rf $pathToOwaspSources; mkdir $pathToOwaspSources")
            fsi.downloadFilesToRemote(remoteToLocalPaths)
            fsi.execCommand("cd ~/vulnomicon; rm -rf $pathToOwasp-output; ./scripts/runBenchmarkJavaMutated.sh")
            val reportsDir = "$pathToOwasp-output"
            val reportsPaths = fsi.execCommand("cd ~/vulnomicon; find $reportsDir -name \"*.sarif\"")!!
            val pathToReports =
                reportsPaths
                    .split("\n")
                    .drop(1)
                    .dropLast(1)
                    .filterNot { it.contains("truth") }
                    .associateWith { "tmp/scorecards/${it.substringAfterLast('/')}" }
            fsi.downloadFilesFromRemote(pathToReports)
        } else {
            with(ProcessBuilder()) {
                try {
                    command("bash", "-c", cmdToRm).start().waitFor()
                } catch (e: IOException) { }
                try {
                    command("bash", "-c", "rm -rf $pathToOwaspSources; mkdir $pathToOwaspSources").start()
                        .waitFor()
                } catch (e: IOException) {}
                remoteToLocalPaths.entries.map {
                    val cmd = "cp ${Paths.get(it.value).absolutePathString()} ${it.key}"
                    command("bash", "-c", cmd).start().waitFor()
                }
                val execCommand =
                    "cd ~/vulnomicon; rm -rf $pathToOwasp-output; ./scripts/runBenchmarkJavaMutated.sh"
                command("bash", "-c", execCommand).start().let { process ->
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println(line)
                    }
                    reader.close()
                    process.waitFor()
                }
                val reportsDir = "$pathToOwasp-output"
                val scoreCardsPaths = StringBuilder()
                command("bash", "-c", "find $reportsDir -name \"*.sarif\"").start().let { process ->
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        scoreCardsPaths.appendLine(line)
                    }
                    reader.close()
                    process.waitFor()
                }
                val pathToReports = scoreCardsPaths
                    .split("\n")
                    .dropLast(1)
                    .associateWith { "tmp/scorecards/${it.substringAfterLast('/')}" }
                val commandToCpScoreCards = pathToReports.entries.joinToString("; "){"cp ${it.key} ${it.value}"}
                command("bash", "-c", commandToCpScoreCards).start().waitFor()
            }
        }
    }
}