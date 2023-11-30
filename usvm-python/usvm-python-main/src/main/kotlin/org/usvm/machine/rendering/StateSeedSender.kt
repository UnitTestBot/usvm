package org.usvm.machine.rendering

import org.usvm.UConcreteHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isStaticHeapRef
import org.usvm.language.types.ConcretePythonType
import org.usvm.machine.PythonExecutionState
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.saving.GeneratedPythonObject
import org.usvm.machine.saving.PythonAnalysisResultSaver
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.utils.PyModelHolder
import org.usvm.types.first

class StateSeedSender<InputRepr>(
    private val saver: PythonAnalysisResultSaver<InputRepr>
) {
    fun getData(state: PythonExecutionState): InputRepr? = runCatching {
        val converter = if (state.meta.lastConverter != null) {
            state.meta.lastConverter!!
        } else {
            val modelHolder = PyModelHolder(state.pyModel)
            ConverterToPythonObject(
                state.ctx,
                state.typeSystem,
                modelHolder,
                state.preAllocatedObjects,
                state.memory,
                useNoneInsteadOfVirtual = true
            )
        }
        converter.restart()
        val inputs = state.inputSymbols.map {
            val interpretedRaw = state.pyModel.eval(it.address) as UConcreteHeapRef
            val interpreted = if (isStaticHeapRef(interpretedRaw)) {
                val type = state.memory.typeStreamOf(interpretedRaw).first()
                require(type is ConcretePythonType)
                InterpretedAllocatedOrStaticSymbolicPythonObject(interpretedRaw, type, state.typeSystem)
            } else {
                InterpretedInputSymbolicPythonObject(interpretedRaw, converter.modelHolder, state.typeSystem)
            }
            val type = interpreted.getConcreteType() ?: state.typeSystem.pythonNoneType
            val concrete = converter.convert(interpreted)
            GeneratedPythonObject(concrete, type, interpreted)
        }
        val serialized = saver.serializeInput(inputs, converter)
        inputs.forEach {
            ConcretePythonInterpreter.decref(it.ref)
        }
        return serialized
    }.getOrNull()

    suspend fun sendStateSeeds(data: InputRepr) {
        // println("Sending!")
        saver.saveNextInputs(data)
    }
}