package org.usvm.language.types

import org.usvm.machine.interpreters.ConcretePythonInterpreter

object PythonAnyType: VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean = true
}

object ArrayType: VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        return type == this || type is ArrayLikeConcretePythonType
    }
}

class ConcreteTypeNegation(private val concreteType: ConcretePythonType): VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        if (type is MockType)
            return true
        if (type !is ConcretePythonType)
            return false
        return type != concreteType
    }
}

sealed class TypeProtocol: VirtualPythonType() {
    abstract fun acceptsConcrete(type: ConcretePythonType): Boolean
    override fun accepts(type: PythonType): Boolean {
        if (type == this || type is MockType)
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

object HasNbAdd: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbAdd(type.asObject)
}

object HasNbSubtract: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbSubtract(type.asObject)
}

object HasNbMultiply: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbMultiply(type.asObject)
}

object HasNbMatrixMultiply: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbMatrixMultiply(type.asObject)
}

object HasSqLength: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasSqLength(type.asObject)
}

object HasMpLength: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasMpLength(type.asObject)
}

object HasMpSubscript: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasMpSubscript(type.asObject)
}

object HasMpAssSubscript: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasMpAssSubscript(type.asObject)
}

object HasTpRichcmp: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpRichcmp(type.asObject)
}

object HasTpIter: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpIter(type.asObject)
}