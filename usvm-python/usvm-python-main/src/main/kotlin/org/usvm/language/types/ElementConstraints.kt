package org.usvm.language.types

import org.usvm.*
import org.usvm.api.readField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.TimeOfCreation
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.model.PyModel
import org.usvm.machine.model.getConcreteType
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

abstract class ElementConstraint {
    abstract fun applyUninterpreted(
        array: UninterpretedSymbolicPythonObject,
        element: UninterpretedSymbolicPythonObject,
        state: PyState
    ): UBoolExpr

    abstract fun applyInterpreted(
        array: UConcreteHeapRef,
        element: UConcreteHeapRef,
        model: PyModel,
        ctx: PyContext
    ): Boolean
}

object NonRecursiveConstraint: ElementConstraint() {
    override fun applyUninterpreted(
        array: UninterpretedSymbolicPythonObject,
        element: UninterpretedSymbolicPythonObject,
        state: PyState
    ): UBoolExpr = with(state.ctx) {
        if (element.address is UConcreteHeapRef)
            return trueExpr
        if (element.address is UNullRef)
            return trueExpr
        mkIteNoSimplify(
            mkHeapRefEq(element.address, nullRef),
            trueExpr,
            element.getTimeOfCreation(state) lt array.getTimeOfCreation(state)
        )
    }

    override fun applyInterpreted(
        array: UConcreteHeapRef,
        element: UConcreteHeapRef,
        model: PyModel,
        ctx: PyContext
    ): Boolean = with(ctx) {
        if (element.address == 0 || element.address > 0)
            return true
        (model.readField(element, TimeOfCreation, intSort) lt model.readField(array, TimeOfCreation, intSort)).isTrue
    }

}

class GenericConstraint(val innerType: PythonType): ElementConstraint() {
    override fun applyUninterpreted(
        array: UninterpretedSymbolicPythonObject,
        element: UninterpretedSymbolicPythonObject,
        state: PyState
    ): UBoolExpr {
        return state.pathConstraints.typeConstraints.evalIsSubtype(element.address, innerType)
    }

    override fun applyInterpreted(
        array: UConcreteHeapRef,
        element: UConcreteHeapRef,
        model: PyModel,
        ctx: PyContext
    ): Boolean {
        if (element.address == 0)
            return true
        require(!isStaticHeapRef(element) && !isAllocatedConcreteHeapRef(element))
        return model.types.evalIsSubtype(element, innerType).isTrue
    }

    override fun equals(other: Any?): Boolean {
        if (other !is GenericConstraint)
            return false
        return innerType == other.innerType
    }

    override fun hashCode(): Int {
        return innerType.hashCode()
    }
}