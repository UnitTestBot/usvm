package org.usvm.language.types

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace

sealed class PythonType

object TypeOfVirtualObject: PythonType()

abstract class VirtualPythonType: PythonType() {
    abstract fun accepts(type: PythonType): Boolean
}

data class ConcretePythonType(val typeName: String, val asObject: PythonObject): PythonType()

private fun createConcreteType(name: String) = ConcretePythonType(name, ConcretePythonInterpreter.eval(emptyNamespace, name))

val pythonInt = createConcreteType("int")
val pythonBool = createConcreteType("bool")
val pythonObjectType = createConcreteType("object")
val pythonNoneType = ConcretePythonType("NoneType", ConcretePythonInterpreter.eval(emptyNamespace, "type(None)"))
val pythonTypeType = createConcreteType("type")
val pythonList = createConcreteType("list")
val pythonTuple = createConcreteType("tuple")
val pythonListIteratorType = ConcretePythonType(
    "list_iterator",
    ConcretePythonInterpreter.eval(emptyNamespace, "type(iter([]))")
)