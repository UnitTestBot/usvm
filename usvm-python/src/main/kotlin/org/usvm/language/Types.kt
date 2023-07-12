package org.usvm.language

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.emptyNamespace
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

sealed class PythonType

sealed class VirtualPythonType: PythonType()
object PythonAnyType: VirtualPythonType()

data class ConcretePythonType(val typeName: String, val asObject: PythonObject): PythonType()

private fun createConcreteType(name: String) = ConcretePythonType(name, ConcretePythonInterpreter.eval(emptyNamespace, name))

val pythonInt = createConcreteType("int")
val pythonBool = createConcreteType("bool")
val pythonList = createConcreteType("list")

object PythonTypeSystem: UTypeSystem<PythonType> {
    override fun isSupertype(u: PythonType, t: PythonType): Boolean { // TODO
        if (u == PythonAnyType)
            return true
        return u == t
    }

    override fun isMultipleInheritanceAllowedFor(t: PythonType): Boolean {  // TODO
        return t == PythonAnyType
    }

    override fun isFinal(t: PythonType): Boolean {  // TODO
        return t != PythonAnyType
    }

    override fun isInstantiable(t: PythonType): Boolean {
        return t is ConcretePythonType
    }

    override fun findSubtypes(t: PythonType): Sequence<PythonType> {
        if (t == PythonAnyType)
            return sequenceOf(pythonInt, pythonBool)
        return sequenceOf(t)
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

}