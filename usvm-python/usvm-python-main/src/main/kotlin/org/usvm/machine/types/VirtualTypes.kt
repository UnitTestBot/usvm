package org.usvm.machine.types

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.annotations.ids.SlotId

abstract class VirtualPythonType : PythonType() {
    abstract fun accepts(type: PythonType): Boolean
}

object PythonAnyType : VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean = true
}

object ArrayType : VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        return type == this || type is ArrayLikeConcretePythonType
    }
}

data class HasElementConstraint(private val constraint: ElementConstraint) : VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        if (type == this) {
            return true
        }
        if (type !is ArrayLikeConcretePythonType) {
            return false
        }
        return type.elementConstraints.contains(constraint)
    }
}

data class ConcreteTypeNegation(val concreteType: ConcretePythonType) : VirtualPythonType() {
    override fun accepts(type: PythonType): Boolean {
        if (type is MockType || type == this) {
            return true
        }
        if (type !is ConcretePythonType) {
            return false
        }
        return type != concreteType
    }
}

/*
 * Temporary slotId is nullable,
 * since some slots, such as nb_int, nb_index and mp_length,
 * are missing their implementation in virtual_objects.c
 */
sealed class TypeProtocol(val slotId: SlotId? = null) : VirtualPythonType() {
    abstract fun acceptsConcrete(type: ConcretePythonType): Boolean
    override fun accepts(type: PythonType): Boolean {
        if (type == this || type is MockType) {
            return true
        }
        if (type !is ConcretePythonType) {
            return false
        }
        return acceptsConcrete(type)
    }
}

object HasNbBool : TypeProtocol(SlotId.NbBool) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbBool(type.asObject)
}

object HasNbInt : TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbInt(type.asObject)
}

object HasNbIndex : TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbIndex(type.asObject)
}

object HasNbAdd : TypeProtocol(SlotId.NbAdd) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbAdd(type.asObject)
}

object HasNbSubtract : TypeProtocol(SlotId.NbSubtract) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbSubtract(type.asObject)
}

object HasNbMultiply : TypeProtocol(SlotId.NbMultiply) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbMultiply(type.asObject)
}

object HasNbMatrixMultiply : TypeProtocol(SlotId.NbMatrixMultiply) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbMatrixMultiply(type.asObject)
}

object HasNbNegative : TypeProtocol(SlotId.NbNegative) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbNegative(type.asObject)
}

object HasNbPositive : TypeProtocol(SlotId.NbPositive) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasNbPositive(type.asObject)
}

object HasSqConcat : TypeProtocol(SlotId.SqConcat) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasSqConcat(type.asObject)
}

object HasSqLength : TypeProtocol(SlotId.SqLength) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasSqLength(type.asObject)
}

object HasMpLength : TypeProtocol() {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasMpLength(type.asObject)
}

object HasMpSubscript : TypeProtocol(SlotId.MpSubscript) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasMpSubscript(type.asObject)
}

object HasMpAssSubscript : TypeProtocol(SlotId.MpAssSubscript) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasMpAssSubscript(type.asObject)
}

object HasTpRichcmp : TypeProtocol(SlotId.TpRichcompare) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpRichcmp(type.asObject)
}

// TODO: since we cannot turn off this slot on virtual object, we may need to remove this [TypeProtocol] in the future.
object HasTpGetattro : TypeProtocol(SlotId.TpGetattro) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpGetattro(type.asObject)
}

// TODO: since we cannot turn off this slot on virtual object, we may need to remove this [TypeProtocol] in the future.
object HasTpSetattro : TypeProtocol(SlotId.TpSetattro) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpSetattro(type.asObject)
}

object HasTpIter : TypeProtocol(SlotId.TpIter) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpIter(type.asObject)
}

object HasTpCall : TypeProtocol(SlotId.TpCall) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpCall(type.asObject)
}

// TODO: since we cannot turn off this slot on virtual object, we may need to remove this [TypeProtocol] in the future.
object HasTpHash : TypeProtocol(SlotId.TpHash) {
    override fun acceptsConcrete(type: ConcretePythonType): Boolean =
        ConcretePythonInterpreter.typeHasTpHash(type.asObject)
}
