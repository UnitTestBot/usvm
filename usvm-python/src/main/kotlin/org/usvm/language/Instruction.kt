package org.usvm.language

sealed class PythonInstruction
data class PythonOPCODE(val numberInBytecode: Int): PythonInstruction()
data class MethodQuery(
    val methodId: Int,
    val operands: List<SymbolForCPython>,
    val result: SymbolForCPython
): PythonInstruction()