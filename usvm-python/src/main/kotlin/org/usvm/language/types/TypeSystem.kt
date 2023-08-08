package org.usvm.language.types

import org.usvm.machine.interpreters.PythonObject
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

object PythonTypeSystem: UTypeSystem<PythonType> {
    override fun isSupertype(supertype: PythonType, type: PythonType): Boolean {
        if (supertype is VirtualPythonType)
            return supertype.accepts(type)
        return supertype == type
    }

    override fun isMultipleInheritanceAllowedFor(type: PythonType): Boolean {
        return !isInstantiable(type)
    }

    override fun isFinal(type: PythonType): Boolean {
        return isInstantiable(type)
    }

    override fun isInstantiable(type: PythonType): Boolean {
        return type is ConcretePythonType || type is TypeOfVirtualObject
    }

    private val addressToConcreteType = mapOf(
        pythonInt.asObject to pythonInt,
        pythonBool.asObject to pythonBool,
        pythonObjectType.asObject to pythonObjectType,
        pythonNoneType.asObject to pythonNoneType,
        pythonList.asObject to pythonList
    )

    private val basicConcretePythonTypes = addressToConcreteType.values

    override fun findSubtypes(type: PythonType): Sequence<PythonType> {
        if (isFinal(type))
            return emptySequence()
        return (listOf(TypeOfVirtualObject) + basicConcretePythonTypes.filter { isSupertype(type, it) }).asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

    fun getConcreteTypeByAddress(typeAsObject: PythonObject): ConcretePythonType? =
        addressToConcreteType[typeAsObject]

}