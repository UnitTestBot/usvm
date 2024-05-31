package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject


object ObjectWithDictSerializer : PythonObjectSerializer<String> {
    override fun serialize(obj: PyObject): String {
        val objRepr = ReprObjectSerializer.serialize(obj)
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.addObjectToNamespace(namespace, obj, "obj")
        return runCatching {
            val dict = ConcretePythonInterpreter.eval(namespace, "obj.__dict__")
            if (ConcretePythonInterpreter.getPythonObjectTypeName(dict) == "dict") {
                val dictRepr = ReprObjectSerializer.serialize(dict)
                "$objRepr with dict $dictRepr"
            } else {
                objRepr
            }
        }.getOrDefault(objRepr)
    }
}
