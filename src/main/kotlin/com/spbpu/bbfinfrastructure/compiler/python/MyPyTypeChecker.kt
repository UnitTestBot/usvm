package com.spbpu.bbfinfrastructure.compiler.python

import com.spbpu.bbfinfrastructure.compiler.CommonCompiler
import com.spbpu.bbfinfrastructure.compiler.CompilingResult
import com.spbpu.bbfinfrastructure.compiler.KotlincInvokeStatus
import com.spbpu.bbfinfrastructure.compiler.Stream
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.test.ErrorCollector
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.getAllWithout
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import java.io.File

class MyPyTypeChecker:  CommonCompiler() {

    val myPyExecutor = PythonToolExecutor("mypy --ignore-missing-imports --disable-error-code name-defined --disable-error-code method-assign .")
    val pyTypeExecutor = PythonToolExecutor("pytype --disable name-error,import-error .")


    override val arguments: String = ""
    override val compilerInfo: String = "PyType"
    override var pathToCompiled: String = ""

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
        val directoryWithFile = project.configuration.getDirOfOriginalFile()
        project.files.getAllWithout(0).forEach { bbfFile ->
            with(File("$directoryWithFile/${bbfFile.name}")) {
                if (!exists()) {
                    writeText(bbfFile.psiFile.text)
                }
            }
        }
        val myPyExecutionResults = myPyExecutor.exec(directoryWithFile)
        if (!myPyExecutionResults.contains("Success: no issues found")) {
            if (FuzzingConf.testMode) {
                ErrorCollector.putError("MyPy ERROR: $myPyExecutionResults")
            }
            return CompilingResult(-1, "")
        }
        val pyTypeExecutionResult = pyTypeExecutor.exec(directoryWithFile)
        return if (pyTypeExecutionResult.contains("Success: no errors found")) {
            CompilingResult(0, "")
        } else {
            if (FuzzingConf.testMode) {
                ErrorCollector.putError("PyType ERROR: $pyTypeExecutionResult")
            }
            CompilingResult(-1, "")
        }
    }

    override fun exec(path: String, streamType: Stream, mainClass: String): String {
        TODO("Not yet implemented")
    }


}