package com.spbpu.bbfinfrastructure.compiler

import com.spbpu.bbfinfrastructure.project.Directives
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import org.apache.commons.io.FileUtils
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
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

    private fun prepareArgs(project: Project, path: String, destination: String): K2JVMCompilerArguments {
        val destFile = File(destination)
        if (destFile.isFile) destFile.delete()
        else if (destFile.isDirectory) FileUtils.cleanDirectory(destFile)
        val projectArgs = project.getProjectSettingsAsCompilerArgs("JVM") as K2JVMCompilerArguments
        val compilerArgs =
            if (arguments.isEmpty())
                "$path -d $destination".split(" ")
            else
                "$path $arguments -d $destination".split(" ")
        projectArgs.apply { K2JVMCompiler().parseArguments(compilerArgs.toTypedArray(), this) }
        //projectArgs.compileJava = true
        projectArgs.classpath =
            "${
                CompilerArgs.jvmStdLibPaths.joinToString(
                    separator = ":"
                )
            }:${System.getProperty("java.class.path")}"
                .split(":")
                .filter { it.isNotEmpty() }
                .toSet().toList()
                .joinToString(":")
        projectArgs.jvmTarget = "1.8"
        projectArgs.optIn = arrayOf("kotlin.ExperimentalStdlibApi", "kotlin.contracts.ExperimentalContracts")
        if (project.configuration.jvmDefault.isNotEmpty())
            projectArgs.jvmDefault = project.configuration.jvmDefault.substringAfter(Directives.jvmDefault)
        return projectArgs
    }

    override fun tryToCompile(project: Project): KotlincInvokeStatus {
        val compilationResult = compile(project)
        println("COMPIlATION RESULT = ${compilationResult.status}")
        return KotlincInvokeStatus("", compilationResult.status == 0, compilationResult.status == 0, false)
    }

    override fun isCompilerBug(project: Project): Boolean {
        TODO("Not yet implemented")
    }

    override fun compile(project: Project, includeRuntime: Boolean): CompilingResult {
        File(CompilerArgs.pathToTmpJava).apply {
            deleteRecursively()
            mkdir()
        }
        val pathToFiles = project.saveOrRemoveToTmp(true).split(" ").map { File(it) }
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val sources = manager.getJavaFileObjectsFromFiles(pathToFiles)
        val pathToTmpDir = CompilerArgs.pathToTmpFile.substringBeforeLast('/') + "/tmp"
        File(pathToTmpDir).deleteRecursively()
        val classPath =
            (CompilerArgs.pathToOwaspJar)
        val options = mutableListOf("-classpath", classPath, "-d", pathToTmpDir)
        val task = compiler.getTask(null, manager, diagnostics, options, null, sources)
        task.call()
        val errorDiagnostics = diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
        if (errorDiagnostics.isNotEmpty()) {
            println("COMPILATION ERROR: ${errorDiagnostics.joinToString("\n")}")
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