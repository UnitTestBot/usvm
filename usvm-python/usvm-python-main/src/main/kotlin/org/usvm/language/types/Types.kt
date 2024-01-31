package org.usvm.language.types

import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.python.model.PyIdentifier

sealed class PythonType

object MockType: PythonType()

open class InternalType: PythonType()

sealed class InternalDictType: InternalType()
object ObjectDictType: InternalDictType()
object RefDictType: InternalDictType()
object IntDictType: InternalDictType()

sealed class InternalSetType: InternalType()
object RefSetType: InternalSetType()
object IntSetType: InternalSetType()

abstract class VirtualPythonType: PythonType() {
    abstract fun accepts(type: PythonType): Boolean
}

sealed class ConcretePythonType(
    val owner: PythonTypeSystem,
    val typeName: String,
    val id: PyIdentifier,
    val isHidden: Boolean = false,
    val addressGetter: () -> PyObject
): PythonType() {
    open val asObject: PyObject
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
    addressGetter: () -> PyObject
): ConcretePythonType(owner, typeName, id, isHidden, addressGetter)

class ArrayLikeConcretePythonType(
    val elementConstraints: Set<ElementConstraint>,
    owner: PythonTypeSystem,
    typeName: String,
    id: PyIdentifier,
    val original: ArrayLikeConcretePythonType? = null,
    val innerType: PythonType? = null,
    addressGetter: () -> PyObject
): ConcretePythonType(owner, typeName, id,false, addressGetter) {
    override val asObject: PyObject
        get() = original?.let {
            owner.addressOfConcreteType(it)
        } ?: owner.addressOfConcreteType(this)

    override fun equals(other: Any?): Boolean {
        if (other !is ArrayLikeConcretePythonType)
            return false
        return id == other.id && innerType == other.innerType
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (innerType?.hashCode() ?: 0)
        return result
    }

    init {
        require(innerType == null || owner.isInstantiable(innerType)) {
            "innerType must be instantiable"
        }
        if (original == null) {
            require(innerType == null)
        } else {
            require(original.original == null && innerType != null)
        }
    }

    override fun toString(): String = "$id[$innerType]"

    fun substitute(innerType: PythonType): ArrayLikeConcretePythonType {
        require(original == null) {
            "Can substitute only from original"
        }
        return ArrayLikeConcretePythonType(
            elementConstraints + GenericConstraint(innerType),
            owner,
            typeName,
            id,
            this,
            innerType,
            addressGetter
        )
    }
}