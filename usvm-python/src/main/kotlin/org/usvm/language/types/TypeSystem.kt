package org.usvm.language.types

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

    private val basicConcretePythonTypes = listOf(
        pythonInt,
        pythonBool,
        pythonObjectType,
        pythonNoneType
    )

    override fun findSubtypes(type: PythonType): Sequence<PythonType> {
        if (isFinal(type))
            return emptySequence()
        return (listOf(TypeOfVirtualObject) + basicConcretePythonTypes.filter { isSupertype(type, it) }).asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

}