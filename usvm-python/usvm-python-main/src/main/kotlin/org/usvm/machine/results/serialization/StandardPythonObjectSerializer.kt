package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject

object StandardPythonObjectSerializer : PythonObjectSerializer<PythonObjectInfo> {
    override fun serialize(obj: PyObject): PythonObjectInfo {
        val repr = ReprObjectSerializer.serialize(obj)
        val typeName = ConcretePythonInterpreter.getPythonObjectTypeName(obj)
        val selfTypeName = if (typeName == "type") ConcretePythonInterpreter.getNameOfPythonType(obj) else null
        return PythonObjectInfo(repr, typeName, selfTypeName)
    }
}

class PythonObjectInfo(
    val repr: String,
    val typeName: String,
    val selfTypeName: String?,
) {
    override fun toString(): String = "$repr: $typeName"
}
