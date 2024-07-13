package org.usvm.machine.types

import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.python.model.PyIdentifier

sealed class PythonType

object MockType : PythonType()

open class InternalType : PythonType()

sealed class InternalDictType : InternalType()
object ObjectDictType : InternalDictType()
object RefDictType : InternalDictType()
object IntDictType : InternalDictType()

sealed class InternalSetType : InternalType()
object RefSetType : InternalSetType()
object IntSetType : InternalSetType()

sealed class ConcretePythonType(
    val owner: PythonTypeSystem,
    val typeName: String,
    val id: PyIdentifier,
    val isHidden: Boolean = false,
    val addressGetter: () -> PyObject,
) : PythonType() {
    val asObject: PyObject
        get() = owner.addressOfConcreteType(this)

    val typeModule: String
        get() = id.module

    override fun toString(): String {
        return "ConcretePythonType(\"$typeName\")"
    }
}

class PrimitiveConcretePythonType(
    owner: PythonTypeSystem,
    typeName: String,
    id: PyIdentifier,
    isHidden: Boolean = false,
    addressGetter: () -> PyObject,
) : ConcretePythonType(owner, typeName, id, isHidden, addressGetter)

class ArrayLikeConcretePythonType(
    val elementConstraints: Set<ElementConstraint>,
    owner: PythonTypeSystem,
    typeName: String,
    id: PyIdentifier,
    val innerType: PythonType? = null,
    addressGetter: () -> PyObject,
) : ConcretePythonType(owner, typeName, id, false, addressGetter)
