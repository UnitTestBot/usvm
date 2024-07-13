package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject

object PickleObjectSerializer : PythonObjectSerializer<String?> {
    override fun serialize(obj: PyObject): String? {
        return runCatching {
            val namespace = ConcretePythonInterpreter.getNewNamespace()
            ConcretePythonInterpreter.addObjectToNamespace(namespace, obj, "x")
            ConcretePythonInterpreter.concreteRun(namespace, "import pickle")
            val res = ConcretePythonInterpreter.eval(namespace, "pickle.dumps(x)")
            ConcretePythonInterpreter.decref(namespace)
            ConcretePythonInterpreter.getPythonObjectRepr(res)
        }.getOrNull()
    }
}
