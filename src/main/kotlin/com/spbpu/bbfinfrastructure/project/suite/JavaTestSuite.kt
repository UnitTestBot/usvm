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

class JavaTestSuite : TestSuite() {

    override fun rename(project: Project, bbfFile: BBFFile, projectIndex: Int) {
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

    override fun flushSuiteAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        pathToReportsDir: String,
        isLocal: Boolean
    ) = flushAndRun(
        pathToFuzzBenchmark = pathToFuzzBenchmark,
        scriptToStartBenchmark = scriptToStartBenchmark,
        pathToVulnomicon = pathToVulnomicon,
        isLocal = isLocal,
        pathToReportsDir = pathToReportsDir,
        driverName ="flawgarden-BenchmarkJava-mutated-demo"
    )
}