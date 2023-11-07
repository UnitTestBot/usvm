package org.usvm.language.types

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.utbot.python.newtyping.PythonAnyTypeDescription
import org.utbot.python.newtyping.PythonSubtypeChecker
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.pythonDescription

object PythonAnyType: VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean = true
}

object ArrayType: VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        return type == this || type is ArrayLikeConcretePythonType
    }
}

class HasElementConstraint(private val constraint: ElementConstraint): VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        if (type == this)
            return true
        if (type !is ArrayLikeConcretePythonType)
            return false
        return type.elementConstraints.contains(constraint)
    }
}

class ConcreteTypeNegation(private val concreteType: ConcretePythonType): VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        if (type is MockType || type == this)
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

object HasTpGetattro: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpGetattro(type.asObject)
}

object HasTpSetattro: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpSetattro(type.asObject)
}

object HasTpIter: TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpIter(type.asObject)
}