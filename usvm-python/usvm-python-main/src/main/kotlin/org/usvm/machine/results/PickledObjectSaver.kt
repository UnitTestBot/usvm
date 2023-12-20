package org.usvm.machine.results

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PythonObject

/*
class PickledObjectSaver(
    private val sender: PickledObjectSender
): PythonAnalysisResultSaver<String?>() {
    override fun serializeInput(inputs: List<GeneratedPythonObject>, converter: ConverterToPythonObject): String? {
        val pair = ConcretePythonInterpreter.allocateTuple(2)
        val tuple = ConcretePythonInterpreter.allocateTuple(inputs.size)
        inputs.forEachIndexed { index, generatedPythonObject ->
            ConcretePythonInterpreter.setTupleElement(tuple, index, generatedPythonObject.ref)
        }
        val dict = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.setTupleElement(pair, 0, tuple)
        ConcretePythonInterpreter.setTupleElement(pair, 1, PythonObject(dict.address))
        val result = PickleObjectSerializer.serialize(pair)
        ConcretePythonInterpreter.decref(pair)
        return result
    }

    override fun saveExecutionResult(result: ExecutionResult<PythonObject>) = run { }

    override suspend fun saveNextInputs(input: String?) {
        input?.let { sender.sendPickledInputs(it) }
    }
}

abstract class PickledObjectSender {
    abstract suspend fun sendPickledInputs(pickledInput: String)
}
 */