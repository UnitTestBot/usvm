package org.usvm.machine.saving

import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.rendering.ConverterToPythonObject

fun createStandardSaver(): PythonRepresentationSaver<PythonObjectInfo> =
    PythonRepresentationSaver(StandardPythonObjectSerializer)

fun createReprSaver(): PythonRepresentationSaver<String> =
    PythonRepresentationSaver(ReprObjectSerializer)

fun createDictSaver(): PythonRepresentationSaver<String> =
    PythonRepresentationSaver(ObjectWithDictSerializer)

fun createPickleSaver(): PythonRepresentationSaver<String?> =
    PythonRepresentationSaver(PickleObjectSerializer)

object DummySaver: PythonAnalysisResultSaver<Unit>() {
    override suspend fun saveNextInputs(input: Unit)  = run {}
    override fun saveExecutionResult(result: ExecutionResult<PythonObject>) = run {}
    override fun serializeInput(inputs: List<GeneratedPythonObject>, converter: ConverterToPythonObject) = run {}
}