package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.constraints.UTypeConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.interpreters.operations.myAssert
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.types.UTypeStream
import org.usvm.types.first

sealed class SymbolicPythonObject(
    open val address: UHeapRef,
    val typeSystem: PythonTypeSystem
) {
    override fun equals(other: Any?): Boolean {
        if (other !is SymbolicPythonObject)
            return false
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}

class UninterpretedSymbolicPythonObject(
    address: UHeapRef,
    typeSystem: PythonTypeSystem
): SymbolicPythonObject(address, typeSystem) {
    fun addSupertype(ctx: ConcolicRunContext, type: PythonType) {
        require(ctx.curState != null)
        myAssert(ctx, evalIs(ctx, type))
    }

    fun evalIs(ctx: ConcolicRunContext, type: PythonType): UBoolExpr {
        require(ctx.curState != null)
        return evalIs(ctx.ctx, ctx.curState!!.pathConstraints.typeConstraints, type, ctx)
    }

    fun evalIs(
        ctx: UContext,
        typeConstraints: UTypeConstraints<PythonType>,
        type: PythonType,
        concolicContext: ConcolicRunContext?
    ): UBoolExpr {
        var result: UBoolExpr = typeConstraints.evalIsSubtype(address, type)
        if (type is ConcretePythonType)
            result = with(ctx) { result and mkHeapRefEq(address, nullRef).not() }
        else if (type !is PythonAnyType && concolicContext != null)
            concolicContext.delayedNonNullObjects.add(this)
        return result
    }

    fun setIntContent(ctx: ConcolicRunContext, expr: UExpr<KIntSort>) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonInt)
        val lvalue = UFieldLValue(expr.sort, address, IntContent)
        ctx.curState!!.memory.write(lvalue, expr)
    }

    fun setBoolContent(ctx: ConcolicRunContext, expr: UBoolExpr) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonBool)
        val lvalue = UFieldLValue(expr.sort, address, BoolContent)
        ctx.curState!!.memory.write(lvalue, expr)
    }

    fun setListIteratorContent(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val listLValue = UFieldLValue(addressSort, address, ListOfListIterator)
        ctx.curState!!.memory.write(listLValue, list.address)
        val indexLValue = UFieldLValue(intSort, address, IndexOfListIterator)
        ctx.curState!!.memory.write(indexLValue, mkIntNum(0))
    }

    fun increaseListIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val indexLValue = UFieldLValue(intSort, address, IndexOfListIterator)
        @Suppress("unchecked_cast")
        val oldIndexValue = ctx.curState!!.memory.read(indexLValue) as UExpr<KIntSort>
        ctx.curState!!.memory.write(indexLValue, mkArithAdd(oldIndexValue, mkIntNum(1)))
    }

    fun getListIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val listLValue = UFieldLValue(addressSort, address, ListOfListIterator)
        val indexLValue = UFieldLValue(intSort, address, IndexOfListIterator)
        @Suppress("unchecked_cast")
        val listRef = ctx.curState!!.memory.read(listLValue) as UHeapRef
        @Suppress("unchecked_cast")
        val index = ctx.curState!!.memory.read(indexLValue) as UExpr<KIntSort>
        return listRef to index
    }

    fun getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonInt)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, IntContent, ctx.ctx.intSort) as UExpr<KIntSort>
    }

    fun getToIntContent(ctx: ConcolicRunContext): UExpr<KIntSort>? = with(ctx.ctx) {
        return when (getTypeIfDefined(ctx)) {
            typeSystem.pythonInt -> getIntContent(ctx)
            typeSystem.pythonBool -> mkIte(getBoolContent(ctx), mkIntNum(1), mkIntNum(0))
            else -> null
        }
    }

    fun getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonBool)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, BoolContent, ctx.ctx.boolSort) as UExpr<KBoolSort>
    }

    fun getToBoolValue(ctx: ConcolicRunContext): UBoolExpr? = with (ctx.ctx) {
        require(ctx.curState != null)
        return when (getTypeIfDefined(ctx)) {
            typeSystem.pythonBool -> getBoolContent(ctx)
            typeSystem.pythonInt -> getIntContent(ctx) neq mkIntNum(0)
            typeSystem.pythonList -> ctx.curState!!.memory.heap.readArrayLength(address, typeSystem.pythonList) gt mkIntNum(0)
            else -> null
        }
    }

    fun getTypeIfDefined(ctx: ConcolicRunContext): PythonType? {
        val interpreted = interpretSymbolicPythonObject(this, ctx.modelHolder)
        return interpreted.getConcreteType(ctx)
    }
}

sealed class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    typeSystem: PythonTypeSystem
): SymbolicPythonObject(address, typeSystem) {
    abstract fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType?
    abstract fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort>
    abstract fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort>
}

class InterpretedInputSymbolicPythonObject(
    address: UConcreteHeapRef,
    val modelHolder: PyModelHolder,
    typeSystem: PythonTypeSystem
): InterpretedSymbolicPythonObject(address, typeSystem) {
    init {
        require(address.address <= 0)
    }

    override fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType? = getConcreteType()

    override fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> = getBoolContent(ctx.ctx)

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> = getIntContent(ctx.ctx)

    fun getFirstType(): PythonType? {
        if (address.address == 0)
            return TypeOfVirtualObject
        return modelHolder.model.getFirstType(address)
    }
    fun getConcreteType(): ConcretePythonType? {
        if (address.address == 0)
            return null
        return modelHolder.model.getConcreteType(address)
    }

    fun getTypeStream(): UTypeStream<PythonType>? {
        if (address.address == 0)
            return null
        return modelHolder.model.uModel.typeStreamOf(address)
    }

    fun getIntContent(ctx: UContext): KInterpretedValue<KIntSort> {
        require(getConcreteType() == typeSystem.pythonInt)
        return modelHolder.model.readField(address, IntContent, ctx.intSort)
    }

    fun getBoolContent(ctx: UContext): KInterpretedValue<KBoolSort> {
        require(getConcreteType() == typeSystem.pythonBool)
        return modelHolder.model.readField(address, BoolContent, ctx.boolSort)
    }
}

class InterpretedAllocatedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    typeSystem: PythonTypeSystem
): InterpretedSymbolicPythonObject(address, typeSystem) {
    init {
        require(address.address > 0)
    }
    override fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType? =
        getTypeStream(ctx).first() as? ConcretePythonType

    override fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> {
        require(ctx.curState != null)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, BoolContent, ctx.ctx.boolSort) as KInterpretedValue<KBoolSort>
    }

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> {
        require(ctx.curState != null)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, IntContent, ctx.ctx.intSort) as KInterpretedValue<KIntSort>
    }

    fun getTypeStream(ctx: ConcolicRunContext): UTypeStream<PythonType> {
        require(ctx.curState != null)
        return ctx.curState!!.memory.typeStreamOf(address)
    }
}

fun interpretSymbolicPythonObject(
    obj: UninterpretedSymbolicPythonObject,
    modelHolder: PyModelHolder
): InterpretedSymbolicPythonObject {
    val evaluated = modelHolder.model.eval(obj.address) as UConcreteHeapRef
    if (evaluated.address > 0)
        return InterpretedAllocatedSymbolicPythonObject(evaluated, obj.typeSystem)
    return InterpretedInputSymbolicPythonObject(evaluated, modelHolder, obj.typeSystem)
}