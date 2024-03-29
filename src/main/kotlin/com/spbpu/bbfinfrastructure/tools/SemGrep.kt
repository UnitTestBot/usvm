package com.spbpu.bbfinfrastructure.tools

import com.spbpu.bbfinfrastructure.compiler.JCompiler
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class SemGrep: AnalysisTool {

    val pathToTemplate = "lib/BenchmarkJavaTemplate/src/main/java/org/owasp/benchmark/testcode"
    //TODO handle mappings with commas
    val mappings =
        File("mappings/semgrep.txt")
            .readLines()
            .map { it.split(",") }
            .associate { it.first().trim() to it.last().trim() }


    override fun test(dir: String): Map<String, Set<CWE>> {
        val res = mutableMapOf<String, MutableSet<CWE>>()
        val command =
            "semgrep scan $dir"
        // Start the process
        val builder = ProcessBuilder()
        builder.command("bash", "-c", command) // For Linux/Mac
        // Redirect error stream to output stream
        builder.redirectErrorStream(true)

        println("START SemGrep")
        // Start the process
        val process = builder.start()
        // Wait for process to finish
        process.waitFor()
        println("FINISH SemGrep")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readText()
        val lines = output.split(dir).drop(1)
        for (line in lines) {
            val fileName = line.substringBefore("\n").drop(1)
            Regex("❯❱ (.*)\n").findAll(line).toList().map {
                val errorCodeMessage = it.groupValues.getOrNull(1) ?: return@map
                val cwe = mappings[errorCodeMessage]?.toIntOrNull()
                if (cwe != null) {
                    res.getOrPut(fileName) { mutableSetOf() }.add(CWE(cwe))
                }
            }
        }
        return res
//        return output.split("\n")
//            .map { it.trim() }
//            .filter { it.startsWith("❯❱") }
//            .map { it.substringAfter("❯❱ ") }
//            .mapNotNull { mappings[it]?.toIntOrNull()?.let { CWE(it) } }
    }

//    override fun test(project: Project): List<CWE> {
//        //Save project to benchmark template directory
//        File(pathToTemplate).deleteRecursively()
//        File(pathToTemplate).mkdirs()
//        project.saveToDir(pathToTemplate)
//
//        val command =
//            "semgrep scan ${File(pathToTemplate).absolutePath}"
//        // Start the process
//        val builder = ProcessBuilder()
//        builder.command("bash", "-c", command) // For Linux/Mac
//        // Redirect error stream to output stream
//        builder.redirectErrorStream(true)
//
//        // Start the process
//        val process = builder.start()
//        // Wait for process to finish
//        process.waitFor()
//        val reader = BufferedReader(InputStreamReader(process.inputStream))
//        val output = reader.readText()
//        return output.split("\n")
//            .map { it.trim() }
//            .filter { it.startsWith("❯❱") }
//            .map { it.substringAfter("❯❱ ") }
//            .mapNotNull { mappings[it]?.toIntOrNull()?.let { CWE(it) } }
//    }
}