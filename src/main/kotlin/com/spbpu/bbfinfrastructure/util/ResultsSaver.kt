package com.spbpu.bbfinfrastructure.util

import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesParser
import com.spbpu.bbfinfrastructure.project.LANGUAGE
import com.spbpu.bbfinfrastructure.util.results.ResultHeader
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

class ResultsSaver {

    fun save() {
//        val info = File("res.txt").readText()
//            .split("\n")
//            .filter { it.contains("fail") || it.contains("pass") }
//            .map {
//                it.split(",").last().trim().filter { it != '"' }.split(" ").let { it.first() to it.last() }
//            }.map {
//                val file = File("sortedResults/${it.first}")
//                val header = ResultHeader.convertFromString(file.readText())
//                val cwe = header.originalFileCWE.first()
//                val kind = it.second
//                val name = "src/main/java/org/owasp/benchmark/testcode/" + file.name
//                val original = File("/home/zver/IdeaProjects/vulnomicon/BenchmarkJava/${header.originalFileName}")
//                val originalXml = File("/home/zver/IdeaProjects/vulnomicon/BenchmarkJava/${header.originalFileName}".removeSuffix(".java") + ".xml")
//                val originalCategory = originalXml.readText().substringAfter("<category>").substringBefore("</category>")
//                val testNumber = file.readText().substringAfter("public class BenchmarkTest").substringBefore(" ")
////        val xml = """
////            <test-metadata>
////                <benchmark-version>1.2</benchmark-version>
////                <category>$originalCategory</category>
////                <test-number>$testNumber</test-number>
////                <vulnerability>${kind == "fail"}</vulnerability>
////                <cwe>${cwe}</cwe>
////            </test-metadata>
////        """.trimIndent()
////        xml
////        File("/home/zver/IdeaProjects/vulnomicon/BenchmarkJava-mutated/src/main/java/org/owasp/benchmark/testcode/BenchmarkTest${testNumber}.java").writeText(file.readText())
////        File("/home/zver/IdeaProjects/vulnomicon/BenchmarkJava-mutated/src/main/java/org/owasp/benchmark/testcode/BenchmarkTest${testNumber}.xml").writeText(xml)
//                Triple("src/main/java/org/owasp/benchmark/testcode/BenchmarkTest${testNumber}.java", "kind", cwe)
//            }
//        SarifBuilder().serializeRealResults(info, "", "flawgarden-BenchmarkJava-mutated").let {
//            File("/home/zver/IdeaProjects/vulnomicon/BenchmarkJava-mutated/new_truth.sarif").writeText(it)
//        }
    }

    fun printInfoAboutTemplates() {
        val allTemplates =
            Files.walk(Paths.get("templates-db/languages/python"))
                .map { it.toFile() }
                .filter { it.isFile && it.extension == "tmt" }
                .filter { !it.path.contains("helpers") && !it.path.contains("extensions") }
                .toList()
                .flatMap { TemplatesParser.parse(it.absolutePath).templates.map { it.name } }
                .toSet()
        val results = Files.walk(Paths.get("sortedResults"))
            .map { it.toFile() }
            .toList()
            .filter { it.isFile }
            .map { ResultHeader.convertFromString(it.readText(), LANGUAGE.PYTHON)!!.mutationDescriptionChain.first().substringAfter("name ") }
            .toSet()
        allTemplates - results
        exitProcess(0)
    }
}