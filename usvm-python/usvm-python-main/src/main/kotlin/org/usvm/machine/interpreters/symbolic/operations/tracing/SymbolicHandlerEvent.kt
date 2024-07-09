package org.usvm.machine.interpreters.symbolic.operations.tracing

import org.usvm.language.PyInstruction
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython

sealed class SymbolicHandlerEventParameters<out T>

data class LoadConstParameters(val constToLoad: Any) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class NextInstruction(
    val pyInstruction: PyInstruction,
) : SymbolicHandlerEventParameters<Unit>()

data class PythonFunctionCall(val code: PyObject) : SymbolicHandlerEventParameters<Unit>()

data class PythonReturn(val code: PyObject) : SymbolicHandlerEventParameters<Unit>()

data class Fork(val condition: SymbolForCPython) : SymbolicHandlerEventParameters<Unit>()

data class ForkResult(
    val condition: SymbolForCPython,
    val expectedResult: Boolean,
) : SymbolicHandlerEventParameters<Unit>()

data class Unpack(val iterable: SymbolForCPython, val count: Int) : SymbolicHandlerEventParameters<Unit>()

data class ListCreation(val elements: List<SymbolForCPython>) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class DictCreation(
    val keys: List<SymbolForCPython>,
    val elements: List<SymbolForCPython>,
) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class DictCreationConstKey(
    val keys: SymbolForCPython,
    val elements: List<SymbolForCPython>,
) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class TupleCreation(val elements: List<SymbolForCPython>) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class SetCreation(val elements: List<SymbolForCPython>) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class IsinstanceCheck(
    val on: SymbolForCPython,
    val type: PyObject,
) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class EmptyObjectCreation(val type: PyObject) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class MethodParameters(
    val name: String,
    val operands: List<SymbolForCPython>,
) : SymbolicHandlerEventParameters<SymbolForCPython>()

data class SymbolicMethodParameters(
    val name: String,
    val self: SymbolForCPython?,
    val args: Array<SymbolForCPython>,
) : SymbolicHandlerEventParameters<SymbolForCPython>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SymbolicMethodParameters

        if (name != other.name) return false
        if (self != other.self) return false
        return args.contentEquals(other.args)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + self.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}

data class MethodParametersNoReturn(
    val name: String,
    val operands: List<SymbolForCPython>,
) : SymbolicHandlerEventParameters<Unit>()

class SymbolicHandlerEvent<out T>(
    val parameters: SymbolicHandlerEventParameters<T>,
    val result: T?,
)
