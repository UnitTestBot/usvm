package org.usvm.interpreter.operations.tracing

import org.usvm.language.PythonInstruction
import org.usvm.language.PythonPinnedCallable
import org.usvm.language.SymbolForCPython

sealed class SymbolicHandlerEventParameters<out T>

data class MethodQueryParameters(
    val methodId: Int,
    val operands: List<SymbolForCPython?>
): SymbolicHandlerEventParameters<SymbolForCPython>()

data class LoadConstParameters(val constToLoad: Any): SymbolicHandlerEventParameters<SymbolForCPython>()
data class NextInstruction(val pythonInstruction: PythonInstruction): SymbolicHandlerEventParameters<Unit>()
data class PythonFunctionCall(val function: PythonPinnedCallable): SymbolicHandlerEventParameters<Unit>()
object PythonReturn: SymbolicHandlerEventParameters<Unit>()
data class Fork(val condition: SymbolForCPython): SymbolicHandlerEventParameters<Unit>()

class SymbolicHandlerEvent<out T>(
    val parameters: SymbolicHandlerEventParameters<T>,
    val result: T?
)