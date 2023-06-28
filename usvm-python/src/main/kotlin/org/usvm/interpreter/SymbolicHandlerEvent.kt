package org.usvm.interpreter

import org.usvm.language.PythonInstruction
import org.usvm.language.SymbolForCPython

sealed class SymbolicHandlerEventParameters<out T>

data class MethodQueryParameters(
    val methodId: Int,
    val operands: List<SymbolForCPython?>
): SymbolicHandlerEventParameters<SymbolForCPython>()

data class LoadConstParameters(val constToLoad: Any): SymbolicHandlerEventParameters<SymbolForCPython>()
data class NextInstruction(val pythonInstruction: PythonInstruction): SymbolicHandlerEventParameters<Unit>()

class SymbolicHandlerEvent<out T>(
    val parameters: SymbolicHandlerEventParameters<T>,
    val result: T?
)

/*
data class MethodQuery(
    val methodId: Int,
    val operands: List<SymbolForCPython>,
    val result: SymbolForCPython
): SymbolicHandlerEvent()
*/

// data class NextInstruction(val pythonInstruction: PythonInstruction): SymbolicHandlerEvent()