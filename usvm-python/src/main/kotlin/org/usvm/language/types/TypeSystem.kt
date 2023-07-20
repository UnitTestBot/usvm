package org.usvm.language.types

import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

object PythonTypeSystem: UTypeSystem<PythonType> {
    override fun isSupertype(u: PythonType, t: PythonType): Boolean {
        if (u is VirtualPythonType)
            return u.accepts(t)
        return u == t
    }

    override fun isMultipleInheritanceAllowedFor(t: PythonType): Boolean {
        return t !is ConcretePythonType
    }

    override fun isFinal(t: PythonType): Boolean {
        return t is ConcretePythonType
    }

    override fun isInstantiable(t: PythonType): Boolean {
        return t is ConcretePythonType
    }

    private val basicConcretePythonTypes = listOf(
        pythonInt,
        pythonBool,
        pythonObjectType,
        pythonNoneType
    )

    override fun findSubtypes(t: PythonType): Sequence<PythonType> {
        if (t is ConcretePythonType)
            return emptySequence()
        return basicConcretePythonTypes.filter { isSupertype(t, it) }.asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

}