package com.spbpu.bbfinfrastructure.project

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiIdentifier
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
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class JavaTestSuite {

    val suiteProjects = mutableListOf<Pair<Project, List<MutationInfo>>>()

    fun addProject(project: Project, mutationChain: List<MutationInfo>) {
        val projectIndex = suiteProjects.size
        val mutatedFile = project.files.first()
        rename(mutatedFile, projectIndex)
        suiteProjects.add(project to mutationChain)
    }

    private fun rename(bbfFile: BBFFile, projectIndex: Int) {
        val psiFile = bbfFile.psiFile
        val fileName = bbfFile.name
        val classes =
            psiFile
                .getAllPSIChildrenOfType<PsiClass>()
                .filter { it.name?.let { fileName.contains(it) } ?: false }
        val renamedIdentifiers = mutableMapOf<String, String>()
        for (cl in classes) {
            cl.nameIdentifier?.let {
                renamedIdentifiers[it.text] = "${it.text}$projectIndex"
                val newIdentifier = PsiIdentifierImpl("${it.text}$projectIndex")
                it.replaceThis(newIdentifier)
            }
        }
        psiFile.getAllPSIChildrenOfType<PsiIdentifier>().forEach { identifier ->
            renamedIdentifiers[identifier.text]?.let {
                identifier.replaceThis(PsiIdentifierImpl(it))
            }
        }
        bbfFile.name = "${fileName.substringBefore(".java")}$projectIndex.java"
    }

    private fun fixPath(path: String) =
        if (path.contains("~")) {
            path.substringAfter("/")
        } else {
            path
        }

    fun flushSuiteAndRun(
        pathToBenchmark: String,
        pathToBenchmarkSources: String,
        pathToBenchmarkHelpers: String,
        pathToTruthSarif: String,
        scriptToStartBenchmark: String,
        isLocal: Boolean
    ) {
        val sarifBuilder = SarifBuilder()
        val remoteToLocalPaths = mutableMapOf<String, String>()
        File(CompilerArgs.tmpPath).deleteRecursively()
        File(CompilerArgs.tmpPath).mkdirs()
        val sarif = File("${CompilerArgs.tmpPath}/truth.sarif").apply {
            writeText(sarifBuilder.serialize(suiteProjects, pathToBenchmarkSources.substringAfter("$pathToBenchmark/")))
        }

        for ((project, _) in suiteProjects) {
            val localPaths = project.saveToDir(CompilerArgs.tmpPath)
            localPaths.forEach { localPath ->
                val fileName = localPath.substringAfter(CompilerArgs.tmpPath)
                if (!fileName.contains(project.files.first().name)) {
                    remoteToLocalPaths["${fixPath(pathToBenchmarkHelpers)}/$fileName"] = localPath
                    return@forEach
                }
                val remotePath = "${fixPath(pathToBenchmarkSources)}/$fileName"
                remoteToLocalPaths[remotePath] = localPath
            }
        }
        remoteToLocalPaths[fixPath(pathToTruthSarif)] = sarif.absolutePath
        val cmdToRm =
            remoteToLocalPaths.filterNot { it.key.contains(fixPath(pathToBenchmarkSources)) }.keys.joinToString(" ") { "rm $it;" }
        File("tmp/scorecards/").deleteRecursively()
        File("tmp/scorecards/").mkdirs()
        if (!isLocal) {
            val fsi = FuzzServerInteract()
            fsi.execCommand(cmdToRm)
            fsi.execCommand("rm -rf $pathToBenchmarkSources; mkdir $pathToBenchmarkSources")
            fsi.downloadFilesToRemote(remoteToLocalPaths)
            fsi.execCommand("cd ~/vulnomicon; rm -rf $pathToBenchmark-output-private; $scriptToStartBenchmark")
            val reportsDir = "$pathToBenchmark-output-private"
            val reportsPaths = fsi.execCommand("cd ~/vulnomicon; find $reportsDir -name \"*.sarif\"")!!
            File("tmp/scorecards/").listFiles().forEach { it.delete() }
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
                    command("bash", "-c", cmdToRm.replace("rm ", "rm ~/")).start().waitFor()
                } catch (e: IOException) {
                }
                try {
                    command("bash", "-c", "rm -rf $pathToBenchmarkSources; mkdir $pathToBenchmarkSources").start()
                        .waitFor()
                } catch (e: IOException) {
                }
                remoteToLocalPaths.entries.map {
                    val cmd = "cp ${Paths.get(it.value).absolutePathString()} ~/${it.key}"
                    command("bash", "-c", cmd).start().waitFor()
                }
                val execCommand =
                    "cd ~/vulnomicon; rm -rf $pathToBenchmark-output-private; $scriptToStartBenchmark"
                command("bash", "-c", execCommand).start().let { process ->
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println(line)
                    }
                    reader.close()
                    process.waitFor()
                }
                val reportsDir = "$pathToBenchmark-output-private"
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
                File("tmp/scorecards/").listFiles().forEach { it.delete() }
                val pathToReports = scoreCardsPaths
                    .split("\n")
                    .dropLast(1)
                    .filterNot { it.contains("truth") }
                    .associateWith { "tmp/scorecards/${it.substringAfterLast('/')}" }
                val commandToCpScoreCards = pathToReports.entries.joinToString("; ") { "cp ${it.key} ${it.value}" }
                command("bash", "-c", commandToCpScoreCards).start().waitFor()
            }
        }
    }
}