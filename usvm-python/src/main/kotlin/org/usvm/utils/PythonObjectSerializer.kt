package org.usvm.utils

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject

abstract class PythonObjectSerializer<PythonObjectRepresentation> {
    abstract fun serialize(obj: PythonObject): PythonObjectRepresentation
}

object StandardPythonObjectSerializer: PythonObjectSerializer<PythonObjectInfo>() {
    override fun serialize(obj: PythonObject): PythonObjectInfo {
        val repr = ConcretePythonInterpreter.getPythonObjectRepr(obj)
        val typeName = ConcretePythonInterpreter.getPythonObjectTypeName(obj)
        val selfTypeName = if (typeName == "type") ConcretePythonInterpreter.getNameOfPythonType(obj) else null
        return PythonObjectInfo(repr, typeName, selfTypeName)
    }
}

object ReprObjectSerializer: PythonObjectSerializer<String>() {
    override fun serialize(obj: PythonObject): String {
        return ConcretePythonInterpreter.getPythonObjectRepr(obj)
    }
}

class PythonObjectInfo(
    val repr: String,
    val typeName: String,
    val selfTypeName: String?
) {
    override fun toString(): String = "$repr: $typeName"
}