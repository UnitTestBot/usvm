package org.usvm.machine.saving

import org.usvm.language.types.PythonType
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.symbolicobjects.ConverterToPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject

abstract class PythonAnalysisResultSaver<InputRepr> {
    abstract fun serializeInput(inputs: List<GeneratedPythonObject>, converter: ConverterToPythonObject): InputRepr
    abstract suspend fun saveNextInputs(input: InputRepr)
    abstract fun saveExecutionResult(result: ExecutionResult<PythonObject>)
}

data class GeneratedPythonObject(
    val ref: PythonObject,
    val type: PythonType,
    val asUExpr: InterpretedSymbolicPythonObject
)

sealed class ExecutionResult<PythonObjectRepresentation>
class Success<PythonObjectRepresentation>(
    val output: PythonObjectRepresentation
): ExecutionResult<PythonObjectRepresentation>()

class Fail<PythonObjectRepresentation>(
    val exception: PythonObjectRepresentation
): ExecutionResult<PythonObjectRepresentation>()