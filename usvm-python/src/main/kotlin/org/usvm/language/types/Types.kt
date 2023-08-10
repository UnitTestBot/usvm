package org.usvm.language.types

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace

sealed class PythonType

object TypeOfVirtualObject: PythonType()

abstract class VirtualPythonType: PythonType() {
    abstract fun accepts(type: PythonType): Boolean
}

class ConcretePythonType(
    val typeName: String,
    val asObject: PythonObject,
    val refreshAddress: () -> PythonObject
): PythonType() {
    fun refresh(): ConcretePythonType =
        ConcretePythonType(typeName, refreshAddress(), refreshAddress)

    override fun equals(other: Any?): Boolean {
        if (other !is ConcretePythonType)
            return false
        return asObject == other.asObject
    }

    override fun hashCode(): Int {
        return asObject.hashCode()
    }
}

private fun createConcreteType(refreshAddress: () -> PythonObject): ConcretePythonType {
    val address = refreshAddress()
    val typeName = ConcretePythonInterpreter.getNameOfPythonType(address)
    return ConcretePythonType(typeName, address, refreshAddress)
}
private fun createConcreteType(name: String) = createConcreteType {
    ConcretePythonInterpreter.eval(emptyNamespace, name)
}

val pythonInt = createConcreteType("int")
val pythonBool = createConcreteType("bool")
val pythonObjectType = createConcreteType("object")
val pythonNoneType = createConcreteType("type(None)")
val pythonTypeType = createConcreteType("type")
val pythonList = createConcreteType("list")
val pythonTuple = createConcreteType("tuple")
val pythonListIteratorType = createConcreteType("type(iter([]))")