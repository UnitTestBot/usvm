package org.usvm.language

import org.usvm.UTypeSystem

sealed class PythonType
object PythonInt: PythonType()

object PythonTypeSystem: UTypeSystem<PythonType> {
    override fun isSupertype(u: PythonType, t: PythonType): Boolean {
        TODO("Not yet implemented")
    }

    override fun isMultipleInheritanceAllowedFor(t: PythonType): Boolean {
        TODO("Not yet implemented")
    }

    override fun isFinal(t: PythonType): Boolean {
        TODO("Not yet implemented")
    }

}