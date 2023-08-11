package org.usvm.language.types

import org.usvm.machine.interpreters.PythonObject

sealed class PythonType

object TypeOfVirtualObject: PythonType()

abstract class VirtualPythonType: PythonType() {
    abstract fun accepts(type: PythonType): Boolean
}

class ConcretePythonType internal constructor(
    val owner: PythonTypeSystem,
    val typeName: String,
    val addressGetter: () -> PythonObject
): PythonType() {
    val asObject: PythonObject
        get() = owner.addressOfConcreteType(this)

    override fun toString(): String {
        return "ConcretePythonType(\"$typeName\")"
    }
}