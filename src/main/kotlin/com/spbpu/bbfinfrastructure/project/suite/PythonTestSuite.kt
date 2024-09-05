package com.spbpu.bbfinfrastructure.project.suite

import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import java.io.File

class PythonTestSuite : TestSuite() {

    override fun flushSuiteAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        pathToReportsDir: String,
        isLocal: Boolean
    ) {
        //This is done for CodeQL support
        val testSuite = GlobalTestSuite.pythonTestSuite
        val pathToTargetFile =
            FuzzingConf.pathToBenchmarkToFuzz + "/" + testSuite.suiteProjects.first().first.configuration.originalUri
        val dirWithTargetFile = pathToTargetFile.substringBeforeLast('/')
        var tmpPath = dirWithTargetFile
        val pyFiles = mutableListOf<File>()
        while (tmpPath != FuzzingConf.pathToBenchmarkToFuzz) {
            File(tmpPath).listFiles().forEach {
                if (it.isFile && it.path.endsWith("py") && it.absolutePath != pathToTargetFile) pyFiles.add(it)
            }
            tmpPath = tmpPath.substringBeforeLast('/')
        }
        val backupText = pyFiles.map { it.readText() }
        pyFiles.forEach { pyFile ->
            val importPrefix = dirWithTargetFile
                .removePrefix(pyFile.absolutePath.substringBeforeLast("/"))
                .removePrefix("/")
                .replace('/', '.')
            val importList = testSuite.suiteProjects.map { it.first.files.first().name }.joinToString("\n") { name ->
                "from $importPrefix.${name.substringBeforeLast(".py")} import *"
            }
            pyFile.writeText(importList + "\n" + pyFile.readText())
        }
        try {
            flushAndRun(
                pathToFuzzBenchmark = pathToFuzzBenchmark,
                scriptToStartBenchmark = scriptToStartBenchmark,
                pathToVulnomicon = pathToVulnomicon,
                pathToReportsDir = pathToReportsDir,
                isLocal = isLocal,
                driverName = "flawgarden-PythonMutatedBenchmark-mutated-demo"
            )
        } finally {
            pyFiles.forEachIndexed { index, file -> file.writeText(backupText[index]) }
        }
    }

    override fun rename(project: Project, bbfFile: BBFFile, projectIndex: Int) {
        val fileName = bbfFile.name
        bbfFile.name = "${fileName.substringBefore(".py")}$projectIndex.py"
        project.configuration.let {
            val oldUri = it.mutatedUri
            if (oldUri != null) {
                it.mutatedUri = "${oldUri.substringBeforeLast('/')}/${bbfFile.name}"
            }
        }
    }

}