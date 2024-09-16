package com.spbpu.bbfinfrastructure.compiler

import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.test.ErrorCollector
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import java.io.File
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

class GoCompiler: CommonCompiler() {
    override val arguments: String
        get() = ""

    override fun checkCompiling(project: Project): Boolean =
        compile(project).status == 0

    override fun getErrorMessageWithLocation(project: Project): Pair<String, List<CompilerMessageSourceLocation>> {
        TODO("Not yet implemented")
    }

    override fun tryToCompile(project: Project): KotlincInvokeStatus {
        val compilationResult = compile(project)
        return KotlincInvokeStatus("", compilationResult.status == 0, compilationResult.status == 0, false)
    }

    override fun isCompilerBug(project: Project): Boolean {
        TODO("Not yet implemented")
    }

    override fun compile(project: Project, includeRuntime: Boolean): CompilingResult {
        File(FuzzingConf.pathToTmpGo).apply {
            deleteRecursively()
            mkdir()
        }
        project.saveOrRemoveToTmp(true, FuzzingConf.pathToTmpGo).split(" ").map { File(it) }
        val pathToTmpDir = FuzzingConf.pathToTmpFile.substringBeforeLast('/') + "/tmp"
        File(pathToTmpDir).deleteRecursively()
        val cmd = "cd ${File(FuzzingConf.pathToTmpGo).absolutePath}; go build -o ${File(pathToTmpDir).absolutePath} *"
        with(ProcessBuilder()) {
            command("bash", "-c", cmd)
            redirectErrorStream(true)
            val process = start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            project.saveOrRemoveToTmp(false)
            if (process.exitValue() == 0) {
                println("Go files compiled successfully!")
                return CompilingResult(0, pathToTmpDir)
            } else {
                println("Failed to compile Go files. Output:\n$output")
                return CompilingResult(-1, pathToTmpDir)
            }
        }
    }

    override fun exec(path: String, streamType: Stream, mainClass: String): String {
        TODO("Not yet implemented")
    }

    override val compilerInfo: String = ""
    override var pathToCompiled: String = ""
}