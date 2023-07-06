package org.usvm.language

import org.usvm.UTypeSystem
import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.emptyNamespace

sealed class PythonType

sealed class VirtualPythonType: PythonType()
data class ConcretePythonType(val typeName: String, val asObject: PythonObject): PythonType()

private fun createConcreteType(name: String) = ConcretePythonType(name, ConcretePythonInterpreter.eval(emptyNamespace, name))

val pythonInt = createConcreteType("int")
val pythonBool = createConcreteType("bool")
val pythonList = createConcreteType("list")

object PythonTypeSystem: UTypeSystem<PythonType> {
    override fun isSupertype(u: PythonType, t: PythonType): Boolean { // TODO
        return u == t
    }

    override fun isMultipleInheritanceAllowedFor(t: PythonType): Boolean {  // TODO
        return false
    }

    override fun isFinal(t: PythonType): Boolean {  // TODO
        return true
    }

}