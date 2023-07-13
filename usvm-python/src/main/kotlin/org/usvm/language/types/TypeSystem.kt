package org.usvm.language.types

import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

object PythonTypeSystem: UTypeSystem<PythonType> {
    override fun isSupertype(u: PythonType, t: PythonType): Boolean { // TODO
        if (u == PythonAnyType)
            return true
        if (u == HasNbBool)
            return t == HasNbBool || t == pythonBool
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
        return sequenceOf()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

}