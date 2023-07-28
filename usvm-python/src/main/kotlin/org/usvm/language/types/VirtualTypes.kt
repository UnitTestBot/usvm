package org.usvm.language.types

import org.usvm.interpreter.ConcretePythonInterpreter

object PythonAnyType: VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean = true
}

sealed class TypeProtocol: VirtualPythonType() {
    abstract fun acceptsConcrete(type: ConcretePythonType): Boolean
    override fun accepts(type: PythonType): Boolean {
        if (type == this || type is TypeOfVirtualObject)
            return true
        if (type !is ConcretePythonType)
            return false
        return acceptsConcrete(type)
    }
}

object HasNbBool: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbBool(type.asObject)
}

object HasNbInt: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbInt(type.asObject)
}

object HasTpRichcmp: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpRichcmp(type.asObject)
}