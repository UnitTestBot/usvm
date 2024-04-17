package com.spbpu.bbfinfrastructure.project

import com.intellij.psi.PsiClass
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.server.FuzzServerInteract
import com.spbpu.bbfinfrastructure.tools.SemGrep
import com.spbpu.bbfinfrastructure.tools.SpotBugs
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.replaceThis
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class JavaTestSuite {

    val suiteProjects = mutableListOf<Pair<Project, List<MutationInfo>>>()
    private val analysisTools = listOf(SpotBugs(), SemGrep())
    private val dirToFlushBinaries = "lib/BenchmarkJavaTemplate/binaries"
    private val dirToFlushSources = "lib/BenchmarkJavaTemplate/src/main/java/org/owasp/benchmark/testcode"

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

    fun flushSuiteOnServer(remoteDirForSources: String, pathToCsv: String) {
        val remoteToLocalPaths = mutableMapOf<String, String>()
        val helpersDir = remoteDirForSources.substringBeforeLast('/') + "/helpers/"
        File(CompilerArgs.tmpPath).deleteRecursively()
        File(CompilerArgs.tmpPath).mkdirs()
        val csv = File("${CompilerArgs.tmpPath}/expected_results.csv")
        if (csv.exists()) {
            csv.delete()
        }
        csv.createNewFile()
        csv.appendText("# test name, category, real vulnerability, cwe, Benchmark version: 1.2, 2016-06-1\n")
        for ((project, _) in suiteProjects) {
            val localPaths = project.saveToDir(CompilerArgs.tmpPath)
            localPaths.forEach { localPath ->
                val fileName = localPath.substringAfter(CompilerArgs.tmpPath)
                if (!fileName.contains("Benchmark")) {
                    remoteToLocalPaths["$helpersDir/$fileName"] = localPath
                    return@forEach
                }
                val nameWithoutExt = localPath.substringAfterLast('/').substringBefore(".java")
                //BenchmarkTest00013,xss,true,79
                csv.appendText("$nameWithoutExt,unknown,unknown,0\n")
                val remotePath = "$remoteDirForSources/$fileName"
                remoteToLocalPaths[remotePath] = localPath
            }
        }
        remoteToLocalPaths[pathToCsv] = csv.absolutePath
        val fsi = FuzzServerInteract()
        val cmdToRm =
            remoteToLocalPaths.filterNot { it.key.contains("BenchmarkTest") }.keys.joinToString(" ") { "rm $it;" }
        File("tmp/scorecards/").deleteRecursively()
        File("tmp/scorecards/").mkdirs()
        fsi.execCommand(cmdToRm)
        fsi.execCommand("rm -rf ~/BenchmarkJavaFuzz/src/main/java/org/owasp/benchmark/testcode; mkdir ~/BenchmarkJavaFuzz/src/main/java/org/owasp/benchmark/testcode")
        fsi.downloadFilesToRemote(remoteToLocalPaths)
        fsi.execCommand("cd ~; rm -rf BenchmarkJavaFuzz/scorecard/; rm -rf BenchmarkJavaFuzz/results; positive-benchmark/runReferenceTools.sh; positive-benchmark/createScorecards.sh")
        val scoreCardDir = "/home/stepanov/BenchmarkJavaFuzz/scorecard"
        val scoreCardsPaths = fsi.execCommand("find $scoreCardDir -name \"*Scorecard*.csv\"")!!
        val pathToScoreCards =
            scoreCardsPaths
                .split("\n")
                .drop(1)
                .dropLast(1)
                .associateWith { "tmp/scorecards/${it.substringAfterLast('/')}" }
        fsi.downloadFilesFromRemote(pathToScoreCards)
    }

//    @OptIn(ExperimentalTime::class)
//    fun flushOnDiskAndCheck(): List<Pair<Project, CheckingResult>> {
//        File(dirToFlushSources).deleteRecursively()
//        File(dirToFlushBinaries).deleteRecursively()
//        File(dirToFlushSources).mkdirs()
//        File(dirToFlushBinaries).mkdirs()
//        val resList = mutableListOf<Pair<Project, CheckingResult>>()
//        for (project in suiteProjects) {
//            val compilationResult = JCompiler().compile(project)
//            if (compilationResult.status != 0) continue
//            copyDirectory(compilationResult.pathToCompiled, dirToFlushBinaries)
//            project.saveToDir(dirToFlushSources)
//        }
//        val spotBugsResults = measureTimedValue {
//            SpotBugs().test(dirToFlushBinaries)
//        }.also { println("SPOT BUGS = ${it.duration}") }.value
//        val semGrepResults = measureTimedValue {
//            SemGrep().test(dirToFlushSources)
//        }.also { println("SemGrep BUGS = ${it.duration}") }.value
//        for ((index, project) in suiteProjects.withIndex()) {
//            val semGrepProjectResults =
//                semGrepResults.entries.find { it.key.substringBefore(".java").endsWith("_$index") }?.value
//            val spotBugsProjectResults =
//                spotBugsResults.entries.find { it.key.substringBefore(".java").endsWith("_$index") }?.value
//            println("SEM GREP RESULTS FOR ${project.files.first().name} = $semGrepProjectResults")
//            println("SPOT BUGS RESULTS FOR ${project.files.first().name} = $spotBugsProjectResults")
//            if (semGrepProjectResults == spotBugsProjectResults) {
//                resList.add(project to CheckingResult.EQUAL)
//                println("EQUAL")
//            } else {
//                resList.add(project to CheckingResult.DIFF)
//                println("DIFF")
//            }
//        }
//        suiteProjects.clear()
//        return resList
//    }


    private fun copyDirectory(source: String, target: String) {
        val sourcePath = Paths.get(source)
        val destinationPath = Paths.get(target)

        try {
            Files.walk(sourcePath).use { paths ->
                paths.forEach { path ->
                    try {
                        val destination = destinationPath.resolve(sourcePath.relativize(path))
                        if (Files.isDirectory(path)) {
                            if (Files.notExists(destination)) {
                                Files.createDirectories(destination)
                            }
                        } else {
                            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    enum class CheckingResult {
        EQUAL, DIFF
    }

    class SuiteProject(
        val project: Project,
        val originalFileName: String,
        val initialCWE: String
    )

}