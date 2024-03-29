package com.spbpu.bbfinfrastructure.util

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

object MsgCollector : MessageCollector {
    var hasException = false
    var hasCompileError = false
    var crashMessages = mutableListOf<String>()
    var compileErrorMessages = mutableListOf<String>()
    val locations = mutableListOf<CompilerMessageSourceLocation>()
    val warnings = mutableListOf<String>()

    override fun clear() {
        hasException = false
        hasCompileError = false
        crashMessages.clear()
        compileErrorMessages.clear()
        locations.clear()
        warnings.clear()
    }

    override fun hasErrors(): Boolean {
        return hasException
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        when (severity) {
            CompilerMessageSeverity.EXCEPTION -> {
                hasException = true
                crashMessages.add(message)
                location?.let { locations.add(it) }
            }
            CompilerMessageSeverity.ERROR -> {
                compileErrorMessages.add(message)
                hasCompileError = true
                location?.let { locations.add(it) }
            }
            CompilerMessageSeverity.WARNING, CompilerMessageSeverity.STRONG_WARNING -> {
                warnings.add(message)
            }
        }
    }
}