package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject

object PickleArgsSerializer {
    fun serialize(args: List<PyObject>): String? {
        val pair = ConcretePythonInterpreter.allocateTuple(2)
        val tuple = ConcretePythonInterpreter.allocateTuple(args.size)
        args.forEachIndexed { index, pythonObject ->
            ConcretePythonInterpreter.setTupleElement(tuple, index, pythonObject)
        }
        val dict = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.setTupleElement(pair, 0, tuple)
        ConcretePythonInterpreter.setTupleElement(pair, 1, PyObject(dict.address))
        val result = PickleObjectSerializer.serialize(pair)
        ConcretePythonInterpreter.decref(pair)
        return result
    }
}
