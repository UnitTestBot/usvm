package org.usvm.machine.interpreters.concrete.utils

import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

class SymbolForCPython(
    @JvmField
    val obj: UninterpretedSymbolicPythonObject?,
    @JvmField
    val symbolicTpCall: Long,
) {

    override fun equals(other: Any?): Boolean {
        if (other !is SymbolForCPython) return false
        return obj == other.obj && symbolicTpCall == other.symbolicTpCall
    }

    override fun hashCode(): Int {
        return obj.hashCode()
    }
}
