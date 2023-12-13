package org.usvm.machine.saving

import org.usvm.language.types.PythonType
import org.usvm.machine.interpreters.concrete.PythonObject
import org.usvm.machine.rendering.ConverterToPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject

class PythonRepresentationSaver<PythonObjectRepresentation>(
    private val serializer: PythonObjectSerializer<PythonObjectRepresentation>
): PythonAnalysisResultSaver<Unit>() {
    private val results = mutableListOf<PythonAnalysisResult<PythonObjectRepresentation>>()
    private var currentInputs: List<InputObject<PythonObjectRepresentation>>? = null
    private var currentConverter: ConverterToPythonObject? = null
    override fun serializeInput(
        inputs: List<GeneratedPythonObject>,
        converter: ConverterToPythonObject
    ) {
        currentInputs = inputs.map {
            InputObject(
                it.asUExpr,
                it.type,
                serializer.serialize(it.ref)
            )
        }
        currentConverter = converter
    }

    override suspend fun saveNextInputs(input: Unit) = run { }

    override fun saveExecutionResult(result: ExecutionResult<PythonObject>) {
        require(currentInputs != null && currentConverter != null)
        val serializedResult = when (result) {
            is Success -> Success(serializer.serialize(result.output))
            is Fail -> Fail(serializer.serialize(result.exception))
        }
        results.add(
            PythonAnalysisResult(
                currentConverter!!,
                currentInputs!!,
                serializedResult
            )
        )
    }
    fun getResults(): List<PythonAnalysisResult<PythonObjectRepresentation>> = results
}

data class PythonAnalysisResult<PythonObjectRepresentation>(
    val inputValueConverter: ConverterToPythonObject,
    val inputValues: List<InputObject<PythonObjectRepresentation>>,
    val result: ExecutionResult<PythonObjectRepresentation>
)

data class InputObject<PythonObjectRepresentation>(
    val asUExpr: InterpretedSymbolicPythonObject,
    val type: PythonType,
    val reprFromPythonObject: PythonObjectRepresentation
)