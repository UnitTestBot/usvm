package com.spbpu.bbfinfrastructure.tools

import com.spbpu.bbfinfrastructure.util.CompilerArgs
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class SpotBugs : AnalysisTool {

    val mappings =
        File("mappings/spotbugs.txt").readLines()
            .map {
                it.substringAfterLast('(').filter { it != ')' }.split(',')
            }.associate { it.first() to it.last() }

    override fun test(dir: String): Map<String, Set<CWE>> {
        val res = mutableMapOf<String, MutableSet<CWE>>()
        val command =
            "java -jar ${CompilerArgs.pathToSpotBugs}/spotbugs.jar  -textui -longBugCodes -effort:max" +
                    " -auxclasspath ${CompilerArgs.pathToOwaspJar} " + dir
        val builder = ProcessBuilder()
        builder.command("bash", "-c", command) // For Linux/Mac
        // Redirect error stream to output stream
        builder.redirectErrorStream(true)

        val output = StringBuilder()
        // Start the process
        val process = builder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            output.appendLine(line)
        }
        // Wait for process to finish
        process.waitFor()
        val listOfBugs = output.toString().split("\n").filter { it.isNotEmpty() }
        for (line in listOfBugs) {
            val regex = Regex("""[A-Z] [A-Z] ([A-Z_]*).* [aA]t (.*\.java).*""")
            val groups = regex.findAll(line).toList().first().groups
            val errorCode = groups[1]?.value ?: continue
            val file = groups[2]?.value ?: continue
            val cwe = mappings[errorCode]?.toIntOrNull() ?: continue
            res.getOrPut(file) { mutableSetOf() }.add(CWE(cwe))
        }
        return res
    }
//
//    override fun test(project: Project): List<CWE> {
//        val compilationResult = JCompiler().compile(project)
//        val command =
//            "java -jar ${CompilerArgs.pathToSpotBugs}/spotbugs.jar  -textui -longBugCodes -effort:max" +
//                    " -auxclasspath ${CompilerArgs.pathToOwaspJar} " + compilationResult.pathToCompiled
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
//        return output
//            .split("\n")
//            .mapNotNull {
//                val bugCode = it.substringAfter(" ").substringAfter(" ").substringBefore(' ')
//                mappings[bugCode]
//            }
//            .mapNotNull { it.toIntOrNull()?.let { CWE(it) } }
//    }


}