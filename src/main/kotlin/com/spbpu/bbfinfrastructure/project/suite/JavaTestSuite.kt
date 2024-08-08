package com.spbpu.bbfinfrastructure.project.suite

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.sarif.SarifBuilder
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.replaceThis
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class JavaTestSuite: TestSuite {

    val suiteProjects = mutableListOf<Pair<Project, List<MutationInfo>>>()

    fun addProject(project: Project, mutationChain: List<MutationInfo>) {
        val projectIndex = suiteProjects.size
        val mutatedFile = project.files.first()
        rename(project, mutatedFile, projectIndex)
        suiteProjects.add(project to mutationChain)
    }

    private fun rename(project: Project, bbfFile: BBFFile, projectIndex: Int) {
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
        project.configuration.let {
            val oldUri = it.mutatedUri
            if (oldUri != null) {
                it.mutatedUri = "${oldUri.substringBeforeLast('/')}/${bbfFile.name}"
            }
        }
    }

    private fun fixPath(path: String) =
        if (path.contains("~")) {
            path.substringAfter("/")
        } else {
            path
        }

    override fun flushSuiteAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        isLocal: Boolean
    ) {
        val sarifBuilder = SarifBuilder()
        val remoteToLocalPaths = mutableMapOf<String, String>()
        File(FuzzingConf.tmpPath).deleteRecursively()
        File(FuzzingConf.tmpPath).mkdirs()
        val sarif = File("${FuzzingConf.tmpPath}/truth.sarif").apply {
            writeText(sarifBuilder.serialize(suiteProjects))
        }

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
        remoteToLocalPaths[fixPath(pathToTruthSarif)] = sarif.absolutePath
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
                    "cd $pathToVulnomicon; rm -rf $pathToFuzzBenchmark-output; $scriptToStartBenchmark"
                command("bash", "-c", execCommand).start().let { process ->
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println(line)
                    }
                    reader.close()
                    process.waitFor()
                }
                val reportsDir = "$pathToFuzzBenchmark-output"
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
                remoteToLocalPaths.keys.forEach { key ->
                    val commandToRm = "rm $key"
                    command("bash", "-c", commandToRm).start().waitFor()
                }
            }
        }
    }
}