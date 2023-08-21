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

    fun addSupertypeSoft(ctx: ConcolicRunContext, type: PythonType) {
        require(ctx.curState != null)
        myAssert(ctx, evalIsSoft(ctx, type))
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
        if (type is ConcretePythonType) {
            return with(ctx) {
                typeConstraints.evalIsSubtype(address, ConcreteTypeNegation(type)).not()
            }
        }
        val result: UBoolExpr = typeConstraints.evalIsSubtype(address, type)
        if (type !is PythonAnyType && concolicContext != null)
            concolicContext.delayedNonNullObjects.add(this)
        return result
    }

    fun evalIsSoft(ctx: ConcolicRunContext, type: PythonType): UBoolExpr {
        require(ctx.curState != null)
        return evalIsSoft(ctx.ctx, ctx.curState!!.pathConstraints.typeConstraints, type, ctx)
    }

    fun evalIsSoft(
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
        val lvalue = UFieldLValue(expr.sort, address, IntContents.content)
        ctx.curState!!.memory.write(lvalue, expr)
    }

    fun getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonInt)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, IntContents.content, ctx.ctx.intSort) as UExpr<KIntSort>
    }

    fun getToIntContent(ctx: ConcolicRunContext): UExpr<KIntSort>? = with(ctx.ctx) {
        return when (getTypeIfDefined(ctx)) {
            typeSystem.pythonInt -> getIntContent(ctx)
            typeSystem.pythonBool -> mkIte(getBoolContent(ctx), mkIntNum(1), mkIntNum(0))
            else -> null
        }
    }

    fun setBoolContent(ctx: ConcolicRunContext, expr: UBoolExpr) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonBool)
        val lvalue = UFieldLValue(expr.sort, address, BoolContents.content)
        ctx.curState!!.memory.write(lvalue, expr)
    }

    fun getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonBool)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, BoolContents.content, ctx.ctx.boolSort) as UExpr<KBoolSort>
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

    fun setListIteratorContent(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val listLValue = UFieldLValue(addressSort, address, ListIteratorContents.list)
        ctx.curState!!.memory.write(listLValue, list.address)
        val indexLValue = UFieldLValue(intSort, address, ListIteratorContents.index)
        ctx.curState!!.memory.write(indexLValue, mkIntNum(0))
    }

    fun increaseListIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val indexLValue = UFieldLValue(intSort, address, ListIteratorContents.index)
        @Suppress("unchecked_cast")
        val oldIndexValue = ctx.curState!!.memory.read(indexLValue) as UExpr<KIntSort>
        ctx.curState!!.memory.write(indexLValue, mkArithAdd(oldIndexValue, mkIntNum(1)))
    }

    fun getListIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val listLValue = UFieldLValue(addressSort, address, ListIteratorContents.list)
        val indexLValue = UFieldLValue(intSort, address, ListIteratorContents.index)
        @Suppress("unchecked_cast")
        val listRef = ctx.curState!!.memory.read(listLValue) as UHeapRef
        @Suppress("unchecked_cast")
        val index = ctx.curState!!.memory.read(indexLValue) as UExpr<KIntSort>
        return listRef to index
    }

    fun setTupleIteratorContent(ctx: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonTupleIteratorType)
        val tupleLValue = UFieldLValue(addressSort, address, TupleIteratorContents.tuple)
        ctx.curState!!.memory.write(tupleLValue, tuple.address)
        val indexLValue = UFieldLValue(intSort, address, TupleIteratorContents.index)
        ctx.curState!!.memory.write(indexLValue, mkIntNum(0))
    }

    fun getTupleIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonTupleIteratorType)
        @Suppress("unchecked_cast")
        val tupleRef = ctx.curState!!.memory.read(UFieldLValue(addressSort, address, TupleIteratorContents.tuple)) as UHeapRef
        @Suppress("unchecked_cast")
        val index = ctx.curState!!.memory.read(UFieldLValue(intSort, address, TupleIteratorContents.index)) as UExpr<KIntSort>
        return tupleRef to index
    }

    fun increaseTupleIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonTupleIteratorType)
        val indexLValue = UFieldLValue(intSort, address, TupleIteratorContents.index)
        @Suppress("unchecked_cast")
        val oldIndexValue = ctx.curState!!.memory.read(indexLValue) as UExpr<KIntSort>
        ctx.curState!!.memory.write(indexLValue, mkArithAdd(oldIndexValue, mkIntNum(1)))
    }

    fun setRangeContent(
        ctx: ConcolicRunContext,
        start: UExpr<KIntSort>,
        stop: UExpr<KIntSort>,
        step: UExpr<KIntSort>
    ) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonRange)
        val startLValue = UFieldLValue(intSort, address, RangeContents.start)
        ctx.curState!!.memory.write(startLValue, start)
        val stopLValue = UFieldLValue(intSort, address, RangeContents.stop)
        ctx.curState!!.memory.write(stopLValue, stop)
        val stepLValue = UFieldLValue(intSort, address, RangeContents.step)
        ctx.curState!!.memory.write(stepLValue, step)
        val lengthLValue = UFieldLValue(intSort, address, RangeContents.length)
        val lengthRValue = mkIte(
            step gt mkIntNum(0),
            mkIte(
                stop gt start,
                mkArithDiv(
                    mkArithAdd(stop, mkArithUnaryMinus(start), step, mkIntNum(-1)),
                    step
                ),
                mkIntNum(0)
            ),
            mkIte(
                start gt stop,
                mkArithDiv(
                    mkArithAdd(start, mkArithUnaryMinus(stop), mkArithUnaryMinus(step), mkIntNum(-1)),
                    mkArithUnaryMinus(step)
                ),
                mkIntNum(0)
            )
        )
        ctx.curState!!.memory.write(lengthLValue, lengthRValue)
    }

    fun setRangeIteratorContent(ctx: ConcolicRunContext, range: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
        val start = ctx.curState!!.memory.read(UFieldLValue(intSort, range.address, RangeContents.start))
        ctx.curState!!.memory.write(UFieldLValue(intSort, address, RangeIteratorContents.start), start)
        val length = ctx.curState!!.memory.read(UFieldLValue(intSort, range.address, RangeContents.length))
        ctx.curState!!.memory.write(UFieldLValue(intSort, address, RangeIteratorContents.length), length)
        val step = ctx.curState!!.memory.read(UFieldLValue(intSort, range.address, RangeContents.step))
        ctx.curState!!.memory.write(UFieldLValue(intSort, address, RangeIteratorContents.step), step)
        val index = mkIntNum(0)
        ctx.curState!!.memory.write(UFieldLValue(intSort, address, RangeIteratorContents.index), index)
    }

    fun getRangeIteratorState(ctx: ConcolicRunContext): Pair<UExpr<KIntSort>, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
        @Suppress("unchecked_cast")
        val index = ctx.curState!!.memory.read(UFieldLValue(intSort, address, RangeIteratorContents.index)) as UExpr<KIntSort>
        @Suppress("unchecked_cast")
        val length = ctx.curState!!.memory.read(UFieldLValue(intSort, address, RangeIteratorContents.length)) as UExpr<KIntSort>
        return index to length
    }

    fun getRangeIteratorNext(ctx: ConcolicRunContext): UExpr<KIntSort> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
        @Suppress("unchecked_cast")
        val index = ctx.curState!!.memory.read(UFieldLValue(intSort, address, RangeIteratorContents.index)) as UExpr<KIntSort>
        val newIndex = mkArithAdd(index, mkIntNum(1))
        ctx.curState!!.memory.write(UFieldLValue(intSort, address, RangeIteratorContents.index), newIndex)
        @Suppress("unchecked_cast")
        val start = ctx.curState!!.memory.read(UFieldLValue(intSort, address, RangeIteratorContents.start)) as UExpr<KIntSort>
        @Suppress("unchecked_cast")
        val step = ctx.curState!!.memory.read(UFieldLValue(intSort, address, RangeIteratorContents.step)) as UExpr<KIntSort>
        return mkArithAdd(start, mkArithMul(index, step))
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
    abstract fun getFirstType(ctx: ConcolicRunContext): PythonType?
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
    override fun getFirstType(ctx: ConcolicRunContext): PythonType? = getFirstType()

    override fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> = getBoolContent(ctx.ctx)

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> = getIntContent(ctx.ctx)

    fun getFirstType(): PythonType? {
        if (address.address == 0)
            return MockType
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
        return modelHolder.model.readField(address, IntContents.content, ctx.intSort)
    }

    fun getBoolContent(ctx: UContext): KInterpretedValue<KBoolSort> {
        require(getConcreteType() == typeSystem.pythonBool)
        return modelHolder.model.readField(address, BoolContents.content, ctx.boolSort)
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

    override fun getFirstType(ctx: ConcolicRunContext): PythonType? = getConcreteType(ctx)

    override fun getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> {
        require(ctx.curState != null)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, BoolContents.content, ctx.ctx.boolSort) as KInterpretedValue<KBoolSort>
    }

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> {
        require(ctx.curState != null)
        @Suppress("unchecked_cast")
        return ctx.curState!!.memory.heap.readField(address, IntContents.content, ctx.ctx.intSort) as KInterpretedValue<KIntSort>
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