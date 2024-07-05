package org.usvm.machine.types

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UNullRef
import org.usvm.api.readField
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.TimeOfCreation
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

interface ElementConstraint {
    fun applyUninterpreted(
        array: UninterpretedSymbolicPythonObject,
        element: UninterpretedSymbolicPythonObject,
        ctx: ConcolicRunContext,
    ): UBoolExpr

    fun applyInterpreted(
        array: UConcreteHeapRef,
        element: UConcreteHeapRef,
        model: PyModel,
        ctx: PyContext,
    ): Boolean
}

object NonRecursiveConstraint : ElementConstraint {
    override fun applyUninterpreted(
        array: UninterpretedSymbolicPythonObject,
        element: UninterpretedSymbolicPythonObject,
        ctx: ConcolicRunContext,
    ): UBoolExpr = with(ctx.ctx) {
        if (element.address is UConcreteHeapRef) {
            return trueExpr
        }
        if (element.address is UNullRef) {
            return trueExpr
        }
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
        ctx: PyContext,
    ): Boolean = with(ctx) {
        if (element.address == 0 || element.address > 0) {
            return true
        }
        (model.readField(element, TimeOfCreation, intSort) lt model.readField(array, TimeOfCreation, intSort)).isTrue
    }
}
