package org.usvm.interpreter.symbolicobjects

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.PyModel
import org.usvm.interpreter.operations.myAssert
import org.usvm.language.*
import org.usvm.language.types.*

sealed class SymbolicPythonObject(open val address: UHeapRef) {
    override fun equals(other: Any?): Boolean {
        if (other !is SymbolicPythonObject)
            return false
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    open fun getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
        myAssert(ctx, ctx.curState.pathConstraints.typeConstraints.evalIs(address, HasNbInt))
        @Suppress("unchecked_cast")
        return ctx.curState.memory.heap.readField(address, IntContent, ctx.ctx.intSort) as UExpr<KIntSort>
    }

    open fun getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
        myAssert(ctx, ctx.curState.pathConstraints.typeConstraints.evalIs(address, HasNbBool))
        @Suppress("unchecked_cast")
        return ctx.curState.memory.heap.readField(address, BoolContent, ctx.ctx.boolSort) as UExpr<KBoolSort>
    }
}

class UninterpretedSymbolicPythonObject(address: UHeapRef): SymbolicPythonObject(address) {
    fun addSupertype(ctx: ConcolicRunContext, type: PythonType) {
        myAssert(ctx, ctx.curState.pathConstraints.typeConstraints.evalIs(address, type))
    }

    fun setIntContent(ctx: ConcolicRunContext, expr: UExpr<KIntSort>) {
        addSupertype(ctx, pythonInt)
        val lvalue = UFieldLValue(expr.sort, address, IntContent)
        ctx.curState.memory.write(lvalue, expr)
    }

    fun setBoolContent(ctx: ConcolicRunContext, expr: UBoolExpr) {
        addSupertype(ctx, pythonBool)
        val lvalue = UFieldLValue(expr.sort, address, BoolContent)
        ctx.curState.memory.write(lvalue, expr)
    }
}

class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    val model: PyModel
): SymbolicPythonObject(address) {
    fun getConcreteType(): ConcretePythonType? = model.getConcreteType(address)
    fun getIntContent(ctx: UContext): KInterpretedValue<KIntSort> {
        require(getConcreteType() == pythonInt) // TODO: types with nb_int ?
        return model.readField(address, IntContent, ctx.intSort)
    }

    fun getBoolContent(ctx: UContext): KInterpretedValue<KBoolSort> {
        require(getConcreteType() == pythonBool)  // TODO: types with nb_bool ?
        return model.readField(address, BoolContent, ctx.boolSort)
    }
}

fun interpretSymbolicPythonObject(
    obj: UninterpretedSymbolicPythonObject,
    model: PyModel
): InterpretedSymbolicPythonObject {
    return InterpretedSymbolicPythonObject(model.eval(obj.address) as UConcreteHeapRef, model)
}