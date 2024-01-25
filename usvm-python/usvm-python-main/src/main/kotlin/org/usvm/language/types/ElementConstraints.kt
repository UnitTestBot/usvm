package org.usvm.language.types

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UNullRef
import org.usvm.api.readField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.machine.symbolicobjects.TimeOfCreation
import org.usvm.machine.PyContext
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.model.nullAddress

abstract class ElementConstraint {
    abstract fun applyUninterpreted(
        array: UninterpretedSymbolicPythonObject,
        element: UninterpretedSymbolicPythonObject,
        ctx: ConcolicRunContext
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
        ctx: ConcolicRunContext
    ): UBoolExpr = with(ctx.ctx) {
        if (element.address is UConcreteHeapRef)
            return trueExpr
        if (element.address is UNullRef)
            return trueExpr
        mkIteNoSimplify(
            mkHeapRefEq(element.address, nullRef),
            trueExpr,
            element.getTimeOfCreation(ctx) lt array.getTimeOfCreation(ctx)
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