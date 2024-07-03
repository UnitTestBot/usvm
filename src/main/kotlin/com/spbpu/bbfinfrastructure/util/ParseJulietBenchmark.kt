package com.spbpu.bbfinfrastructure.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl
import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class ParseJulietBenchmark {

    fun parse(pathToJuliet: String) {
        val ff = Files.walk(Paths.get(pathToJuliet))
            .toList()
            .map { it.toFile() }
            .filter { it.path.endsWith(".java") }
        val names = mutableSetOf<String>()
        val listOfNeededCwe = """CWE23
CWE601
CWE319
CWE321 
CWE325
CWE336
CWE523
CWE759
CWE760
CWE83
CWE113
CWE470
CWE209
CWE256
CWE579
CWE598
CWE15
CWE315
CWE526
CWE259
CWE613"""
        ff.forEachIndexed { index, file ->
            if (listOfNeededCwe.split("\n").map { it.substringBefore(' ') }
                    .all { !file.name.startsWith(it) }) return@forEachIndexed
            if (file.name.matches(Regex(""".*_[0-9]+[a-z].java"""))) {
                return@forEachIndexed
            }
//        if (!file.name.contains("CWE833_Deadlock__synchronized_methods_Servlet_01")) return@forEachIndexed
            println("Handle file $index from ${ff.size} names size = ${names.size} ${file.name}")
            val psi =
                Project.createJavaProjectFromCode(
                    file.readText(),
                    file.name.substringAfterLast("/")
                ).files.first().psiFile
            val goodPsiCopy = psi.copy() as PsiFile
            val badPsiCopy = psi.copy() as PsiFile
            if (!psi.text.contains("public void bad") && !psi.text.contains("public void good")) {
                return@forEachIndexed
            }
            val goodPsiClass = goodPsiCopy.children.first { it is PsiClass } as PsiClass
            val oldIdentifier = goodPsiClass.nameIdentifier?.text ?: "LLSFSAFSV"
            goodPsiClass.nameIdentifier?.let {
                val newIdentifier = PsiIdentifierImpl("${it.text}_good")
                it.replaceThis(newIdentifier)
            }
            goodPsiClass.getAllPSIChildrenOfType<PsiIdentifier>().forEach {
                if (it.text == oldIdentifier) {
                    it.replaceThis(PsiIdentifierImpl("${it.text}_good"))
                }
            }
            val badPsiClass = badPsiCopy.children.first { it is PsiClass } as PsiClass
            badPsiClass.nameIdentifier?.let {
                val newIdentifier = PsiIdentifierImpl("${it.text}_bad")
                it.replaceThis(newIdentifier)
            }
            badPsiClass.getAllPSIChildrenOfType<PsiIdentifier>().forEach {
                if (it.text == oldIdentifier) {
                    it.replaceThis(PsiIdentifierImpl("${it.text}_bad"))
                }
            }
            goodPsiCopy.getAllPSIChildrenOfType<PsiMethod>().map {
                if (it.name == "bad") {
                    val returnStatement = Factory.javaPsiFactory.createCodeBlockFromText("{ return;}", null)
                    it.body?.replaceThis(returnStatement)
                    return@map
                }
                if (it.name.contains("bad", true)) {
                    it.delete()
                }
            }
            badPsiCopy.getAllPSIChildrenOfType<PsiMethod>().map {
                if (it.name == "good") {
                    val returnStatement = Factory.javaPsiFactory.createCodeBlockFromText("{ return;}", null)
                    it.body?.replaceThis(returnStatement)
                    return@map
                }
                if (it.name.contains("good", true)) {
                    it.delete()
                }
            }
            val p1 = Project.createJavaProjectFromCode(goodPsiCopy.text, "${file.nameWithoutExtension}_good.java")
            val p2 = Project.createJavaProjectFromCode(badPsiCopy.text, "${file.nameWithoutExtension}_bad.java")
            //CWE89_SQL_Injection__connect_tcp_execute_22a.java
            if (!file.name.matches(Regex(""".*_[0-9]+[a-z].java"""))) {
                println("GOOD COMPILATION RESULT = ${JCompiler().checkCompiling(p1)}")
                println("BAD COMPILATION RESULT = ${JCompiler().checkCompiling(p2)}")
            }
            File("lib/juliet/testcode/${file.nameWithoutExtension}_good.java").writeText(goodPsiCopy.text)
            File("lib/juliet/testcode/${file.nameWithoutExtension}_bad.java").writeText(badPsiCopy.text)
        }
    }


    fun makeTruthSarif() {
        val results = File("lib/juliet/testcode")
            .listFiles()
            .map { file ->
                ToolsResultsSarifBuilder.ToolExecutionResult(
                    locations = listOf(
                        ToolsResultsSarifBuilder.ResultLocation(
                            ToolsResultsSarifBuilder.ResultPhysicalLocation(
                                ToolsResultsSarifBuilder.ResultArtifactLocation(
                                    "src/main/java/juliet/testcases/${file.name}"
                                )
                            )
                        )
                    ),
                    message = ToolsResultsSarifBuilder.ToolResultMessage("msg"),
                    ruleId = "CWE-${file.name.substringAfter("CWE").substringBefore("_")}",
                    kind = file.nameWithoutExtension.endsWith("bad").ifTrue { "fail" } ?: "pass"
                )
            }
        val sarif = ToolsResultsSarifBuilder.ToolResultSarif(
            `$schema` = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            version = "2.1.0",
            runs = listOf(
                ToolsResultsSarifBuilder.ToolRun(
                    results = results,
                    tool = ToolsResultsSarifBuilder.ToolInfo(ToolsResultsSarifBuilder.ToolDriver("Juliet-BenchmarkJava-v1.3"))
                )
            )
        )
        val json = Json { prettyPrint = true }
        File("lib/juliet/truth.sarif").writeText(json.encodeToString(sarif))
    }
}