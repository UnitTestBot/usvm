package org.usvm.interpreter.symbolicobjects

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.constraints.UTypeConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.PyModel
import org.usvm.interpreter.operations.myAssert
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.types.UTypeStream
import org.usvm.types.first

sealed class SymbolicPythonObject(open val address: UHeapRef) {
    override fun equals(other: Any?): Boolean {
        if (other !is SymbolicPythonObject)
            return false
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}

class UninterpretedSymbolicPythonObject(address: UHeapRef): SymbolicPythonObject(address) {
    fun addSupertype(ctx: ConcolicRunContext, type: PythonType) {
        myAssert(ctx, evalIs(ctx, type))
    }

    fun evalIs(ctx: ConcolicRunContext, type: PythonType): UBoolExpr =
        evalIs(ctx.ctx, ctx.curState.pathConstraints.typeConstraints, type)

    fun evalIs(ctx: UContext, typeConstraints: UTypeConstraints<PythonType>, type: PythonType): UBoolExpr = with(ctx) {
        var result: UBoolExpr = typeConstraints.evalIsSubtype(address, type)
        if (type is ConcretePythonType)
            result = result and mkHeapRefEq(address, nullRef).not()
        return result
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

    fun getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
        addSupertype(ctx, pythonInt)
        @Suppress("unchecked_cast")
        return ctx.curState.memory.heap.readField(address, IntContent, ctx.ctx.intSort) as UExpr<KIntSort>
    }

    fun getToIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> = with(ctx.ctx) {
        return when (getConcreteTypeInModel(ctx)) {
            pythonInt -> getIntContent(ctx)
            pythonBool -> mkIte(getBoolContent(ctx), mkIntNum(1), mkIntNum(0))
            else -> TODO()
        }
    }

    fun getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
        addSupertype(ctx, pythonBool)
        @Suppress("unchecked_cast")
        return ctx.curState.memory.heap.readField(address, BoolContent, ctx.ctx.boolSort) as UExpr<KBoolSort>
    }

    fun getToBoolValue(ctx: ConcolicRunContext): UBoolExpr? {
        val interpreted = interpretSymbolicPythonObject(this, ctx.curState.pyModel)
        with (ctx.ctx) {
            return when (interpreted.getConcreteType(ctx)) {
                pythonBool -> getBoolContent(ctx)
                pythonInt -> getIntContent(ctx) neq mkIntNum(0)
                else -> null
            }
        }
    }

    fun getConcreteTypeInModel(ctx: ConcolicRunContext): ConcretePythonType? {
        val interpreted = interpretSymbolicPythonObject(this, ctx.curState.pyModel)
        return interpreted.getConcreteType(ctx)
    }
}

sealed class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef
): SymbolicPythonObject(address) {
    abstract fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType?
    abstract fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort>
    abstract fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort>
}

class InterpretedInputSymbolicPythonObject(
    address: UConcreteHeapRef,
    val model: PyModel
): InterpretedSymbolicPythonObject(address) {
    init {
        require(address.address <= 0)
    }

    override fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType? = getConcreteType()

    override fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> = getBoolContent(ctx.ctx)

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> = getIntContent(ctx.ctx)

    fun getFirstType(): PythonType? {
        if (address.address == 0)
            return PythonTypeSystem.topTypeStream().first()
        return model.getFirstType(address)
    }
    fun getConcreteType(): ConcretePythonType? {
        if (address.address == 0)
            return null
        return model.getConcreteType(address)
    }

    fun getTypeStream(): UTypeStream<PythonType> {
        if (address.address == 0)
            return PythonTypeSystem.topTypeStream()
        return model.uModel.typeStreamOf(address)
    }

    fun getIntContent(ctx: UContext): KInterpretedValue<KIntSort> {
        require(getConcreteType() == pythonInt)
        return model.readField(address, IntContent, ctx.intSort)
    }

    fun getBoolContent(ctx: UContext): KInterpretedValue<KBoolSort> {
        require(getConcreteType() == pythonBool)
        return model.readField(address, BoolContent, ctx.boolSort)
    }
}

class InterpretedAllocatedSymbolicPythonObject(
    override val address: UConcreteHeapRef
): InterpretedSymbolicPythonObject(address) {
    init {
        require(address.address > 0)
    }
    override fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType? {
        return ctx.curState.memory.typeStreamOf(address).first() as? ConcretePythonType
    }

    override fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> {
        @Suppress("unchecked_cast")
        return ctx.curState.memory.heap.readField(address, BoolContent, ctx.ctx.boolSort) as KInterpretedValue<KBoolSort>
    }

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> {
        @Suppress("unchecked_cast")
        return ctx.curState.memory.heap.readField(address, IntContent, ctx.ctx.intSort) as KInterpretedValue<KIntSort>
    }
}

fun interpretSymbolicPythonObject(
    obj: UninterpretedSymbolicPythonObject,
    model: PyModel
): InterpretedSymbolicPythonObject {
    val evaluated = model.eval(obj.address) as UConcreteHeapRef
    if (evaluated.address > 0)
        return InterpretedAllocatedSymbolicPythonObject(evaluated)
    return InterpretedInputSymbolicPythonObject(evaluated, model)
}