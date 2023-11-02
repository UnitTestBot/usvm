package org.usvm.language.types

import org.usvm.machine.interpreters.PythonObject

sealed class PythonType

object MockType: PythonType()

object ObjectDictType: PythonType()

abstract class VirtualPythonType: PythonType() {
    abstract fun accepts(type: PythonType): Boolean
}

sealed class ConcretePythonType(
    val owner: PythonTypeSystem,
    val typeName: String,
    val isHidden: Boolean = false,
    val addressGetter: () -> PythonObject
): PythonType() {
    val asObject: PythonObject
        get() = owner.addressOfConcreteType(this)

    override fun toString(): String {
        return "ConcretePythonType(\"$typeName\")"
    }
}

class PrimitiveConcretePythonType(
    owner: PythonTypeSystem,
    typeName: String,
    isHidden: Boolean = false,
    addressGetter: () -> PythonObject
): ConcretePythonType(owner, typeName, isHidden, addressGetter)

class ArrayLikeConcretePythonType(
    val elementConstraints: Set<ElementConstraint>,
    owner: PythonTypeSystem,
    typeName: String,
    addressGetter: () -> PythonObject
): ConcretePythonType(owner, typeName, false, addressGetter)
