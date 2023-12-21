package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PythonObject

object ReprObjectSerializer: PythonObjectSerializer<String>() {
    override fun serialize(obj: PythonObject): String {
        return runCatching {
            ConcretePythonInterpreter.getPythonObjectRepr(obj)
        }.getOrDefault("<Error repr for object of type ${ConcretePythonInterpreter.getPythonObjectTypeName(obj)} at ${obj.address}>")
    }
}