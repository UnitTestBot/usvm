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

class JCompiler(override val arguments: String = "") : CommonCompiler() {
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
        File(FuzzingConf.pathToTmpJava).apply {
            deleteRecursively()
            mkdir()
        }
        val pathToFiles = project.saveOrRemoveToTmp(true).split(" ").map { File(it) }
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val sources = manager.getJavaFileObjectsFromFiles(pathToFiles)
        val pathToTmpDir = FuzzingConf.pathToTmpFile.substringBeforeLast('/') + "/tmp"
        File(pathToTmpDir).deleteRecursively()
        val classPath = "${FuzzingConf.pathToOwaspJar}:${FuzzingConf.pathToJulietSupportJar}"
        val options = mutableListOf("-classpath", classPath, "-d", pathToTmpDir)
        val task = compiler.getTask(null, manager, diagnostics, options, null, sources)
        task.call()
        val errorDiagnostics = diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
        if (errorDiagnostics.isNotEmpty()) {
            if (FuzzingConf.testMode) {
                ErrorCollector.putError("COMPILATION ERROR: ${errorDiagnostics.joinToString("\n")}")
            }
        }
        project.saveOrRemoveToTmp(false)
        return if (errorDiagnostics.isEmpty()) {
            CompilingResult(0, pathToTmpDir)
        } else {
            CompilingResult(-1, "")
        }
    }

    override fun exec(path: String, streamType: Stream, mainClass: String): String {
        TODO("Not yet implemented")
    }

    override val compilerInfo: String
        get() = "JVM $arguments"

    override var pathToCompiled: String = "tmp/tmp.jar"


}