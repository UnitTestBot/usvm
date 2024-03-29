package com.spbpu.bbfinfrastructure.compiler

import com.spbpu.bbfinfrastructure.project.Project
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.psi.KtFile
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import org.apache.commons.exec.*

data class CompilingResult(val status: Int, val pathToCompiled: String)

enum class COMPILE_STATUS {
    OK, ERROR, BUG
}

abstract class CommonCompiler {

    abstract val arguments: String
    abstract fun checkCompiling(project: Project): Boolean
    abstract fun getErrorMessageWithLocation(project: Project): Pair<String, List<CompilerMessageSourceLocation>>
    abstract fun tryToCompile(project: Project): KotlincInvokeStatus
    abstract fun isCompilerBug(project: Project): Boolean
    abstract fun compile(project: Project, includeRuntime: Boolean = true): CompilingResult
    abstract fun exec(path: String, streamType: Stream = Stream.INPUT, mainClass: String = ""): String

    abstract val compilerInfo: String
    abstract var pathToCompiled: String

    fun getErrorMessage(project: Project): String = getErrorMessageWithLocation(project).first
    fun getErrorMessageForText(text: String): String = getErrorMessageForTextWithLocation(text).first
    fun getErrorMessageForTextWithLocation(text: String) : Pair<String, List<CompilerMessageSourceLocation>> =
        getErrorMessageWithLocation(Project.createFromCode(text))

    fun tryToCompileWithStatus(project: Project): COMPILE_STATUS {
        val status = tryToCompile(project)
        return when {
            status.isCompileSuccess -> COMPILE_STATUS.OK
            else -> COMPILE_STATUS.ERROR
        }
    }

    fun isCompilerBug(text: String): Boolean {
        if (text.trim().isEmpty()) return false
        return isCompilerBug(Project.createFromCode(text))
    }

    fun checkCompilingText(text: String): Boolean {
        if (text.trim().isEmpty()) return false
        return checkCompiling(Project.createFromCode(text))
    }

    fun commonExec(command: String, streamType: Stream = Stream.INPUT, timeoutSec: Long = 5L): String {
        val cmdLine = CommandLine.parse(command)
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        val executor = DefaultExecutor().also {
            it.watchdog = ExecuteWatchdog(timeoutSec * 1000)
            it.streamHandler = PumpStreamHandler(outputStream, errorStream)
        }
        try {
            executor.execute(cmdLine)
        } catch (e: ExecuteException) {
            executor.watchdog.destroyProcess()
            return when (streamType) {
                Stream.INPUT -> ""
                Stream.ERROR -> errorStream.toString()
                else -> "" + errorStream.toString()
            }
            //return outputStream.toString()
        }
        return when (streamType) {
            Stream.INPUT -> outputStream.toString()
            Stream.ERROR -> errorStream.toString()
            Stream.BOTH -> "OUTPUTSTREAM:\n$outputStream ERRORSTREAM:\n$errorStream"
        }
    }

    override fun toString(): String = compilerInfo
}

