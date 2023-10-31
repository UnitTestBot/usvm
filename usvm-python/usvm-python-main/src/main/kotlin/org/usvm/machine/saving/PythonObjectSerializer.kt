package org.usvm.machine.saving

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject

abstract class PythonObjectSerializer<PythonObjectRepresentation> {
    abstract fun serialize(obj: PythonObject): PythonObjectRepresentation
}

object StandardPythonObjectSerializer: PythonObjectSerializer<PythonObjectInfo>() {
    override fun serialize(obj: PythonObject): PythonObjectInfo {
        val repr = ReprObjectSerializer.serialize(obj)
        val typeName = ConcretePythonInterpreter.getPythonObjectTypeName(obj)
        val selfTypeName = if (typeName == "type") ConcretePythonInterpreter.getNameOfPythonType(obj) else null
        return PythonObjectInfo(repr, typeName, selfTypeName)
    }
}

object ReprObjectSerializer: PythonObjectSerializer<String>() {
    override fun serialize(obj: PythonObject): String {
        return runCatching {
            ConcretePythonInterpreter.getPythonObjectRepr(obj)
        }.getOrDefault("<Error repr for object of type ${ConcretePythonInterpreter.getPythonObjectTypeName(obj)} at ${obj.address}>")
    }
}

object PickleObjectSerializer: PythonObjectSerializer<String?>() {
    override fun serialize(obj: PythonObject): String? {
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

class PythonObjectInfo(
    val repr: String,
    val typeName: String,
    val selfTypeName: String?
) {
    override fun toString(): String = "$repr: $typeName"
}