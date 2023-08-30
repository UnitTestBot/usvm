package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.api.typeStreamOf
import org.usvm.api.writeField
import org.usvm.constraints.UTypeConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.interpreters.operations.myAssert
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.machine.utils.getLeafHeapRef
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
        if (address is UConcreteHeapRef)
            return
        require(ctx.curState != null)
        myAssert(ctx, evalIs(ctx, type))
    }

    fun addSupertypeSoft(ctx: ConcolicRunContext, type: PythonType) {
        if (address is UConcreteHeapRef)
            return
        require(ctx.curState != null)
        myAssert(ctx, evalIsSoft(ctx, type))
    }

    fun evalIs(ctx: ConcolicRunContext, type: PythonType): UBoolExpr {
        require(ctx.curState != null)
        val result = evalIs(ctx.ctx, ctx.curState!!.pathConstraints.typeConstraints, type)
        if (resolvesToNullInCurrentModel(ctx) && ctx.curState!!.pyModel.eval(result).isTrue) {
            ctx.curState!!.possibleTypesForNull = ctx.curState!!.possibleTypesForNull.filterBySupertype(type)
        }
        return result
    }

    fun evalIs(
        ctx: UContext,
        typeConstraints: UTypeConstraints<PythonType>,
        type: PythonType
    ): UBoolExpr {
        if (type is ConcretePythonType) {
            return with(ctx) {
                typeConstraints.evalIsSubtype(address, ConcreteTypeNegation(type)).not()
            }
        }
        return typeConstraints.evalIsSubtype(address, type)
    }

    fun evalIsSoft(ctx: ConcolicRunContext, type: PythonType): UBoolExpr {
        require(ctx.curState != null)
        return evalIsSoft(ctx.ctx, ctx.curState!!.pathConstraints.typeConstraints, type)
    }

    fun evalIsSoft(
        ctx: UContext,
        typeConstraints: UTypeConstraints<PythonType>,
        type: PythonType
    ): UBoolExpr {
        var result: UBoolExpr = typeConstraints.evalIsSubtype(address, type)
        if (type is ConcretePythonType)
            result = with(ctx) { result and mkHeapRefEq(address, nullRef).not() }
        return result
    }

    fun setIntContent(ctx: ConcolicRunContext, expr: UExpr<KIntSort>) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonInt)
        ctx.curState!!.memory.writeField(address, IntContents.content, ctx.ctx.intSort, expr, ctx.ctx.trueExpr)
    }

    fun getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonInt)
        return ctx.curState!!.memory.readField(address, IntContents.content, ctx.ctx.intSort)
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
        return ctx.curState!!.memory.readField(address, BoolContents.content, ctx.ctx.boolSort)
    }

    fun getToBoolValue(ctx: ConcolicRunContext): UBoolExpr? = with (ctx.ctx) {
        require(ctx.curState != null)
        return when (getTypeIfDefined(ctx)) {
            typeSystem.pythonBool -> getBoolContent(ctx)
            typeSystem.pythonInt -> getIntContent(ctx) neq mkIntNum(0)
            typeSystem.pythonList -> ctx.curState!!.memory.readArrayLength(address, typeSystem.pythonList) gt mkIntNum(0)
            else -> null
        }
    }

    fun setListIteratorContent(ctx: ConcolicRunContext, list: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonListIteratorType)
        ctx.curState!!.memory.writeField(address, ListIteratorContents.list, addressSort, list.address, trueExpr)
        ctx.curState!!.memory.writeField(address, ListIteratorContents.index, intSort, mkIntNum(0), trueExpr)
    }

    fun increaseListIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonListIteratorType)
        val oldIndexValue = ctx.curState!!.memory.readField(address, ListIteratorContents.index, intSort)
        ctx.curState!!.memory.writeField(
            address,
            ListIteratorContents.index,
            intSort,
            mkArithAdd(oldIndexValue, mkIntNum(1)),
            trueExpr
        )
    }

    fun getListIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, typeSystem.pythonListIteratorType)
        val listRef = ctx.curState!!.memory.readField(address, ListIteratorContents.list, addressSort)
        val index = ctx.curState!!.memory.readField(address, ListIteratorContents.index, intSort)
        return listRef to index
    }

    fun setTupleIteratorContent(ctx: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
        ctx.curState!!.memory.writeField(address, TupleIteratorContents.tuple, addressSort, tuple.address, trueExpr)
        ctx.curState!!.memory.writeField(address, TupleIteratorContents.index, intSort, mkIntNum(0), trueExpr)
    }

    fun getTupleIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
        val tupleRef = ctx.curState!!.memory.readField(address, TupleIteratorContents.tuple, addressSort)
        val index = ctx.curState!!.memory.readField(address, TupleIteratorContents.index, intSort)
        return tupleRef to index
    }

    fun increaseTupleIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
        val oldIndexValue = ctx.curState!!.memory.readField(address, TupleIteratorContents.index, intSort)
        ctx.curState!!.memory.writeField(
            address,
            TupleIteratorContents.index,
            intSort,
            mkArithAdd(oldIndexValue, mkIntNum(1)),
            trueExpr
        )
    }

    fun setRangeContent(
        ctx: ConcolicRunContext,
        start: UExpr<KIntSort>,
        stop: UExpr<KIntSort>,
        step: UExpr<KIntSort>
    ) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, typeSystem.pythonRange)
        ctx.curState!!.memory.writeField(address, RangeContents.start, intSort, start, trueExpr)
        ctx.curState!!.memory.writeField(address, RangeContents.stop, intSort, stop, trueExpr)
        ctx.curState!!.memory.writeField(address, RangeContents, intSort, step, trueExpr)
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
        ctx.curState!!.memory.writeField(address, RangeContents.length, intSort, lengthRValue, trueExpr)
    }

    fun setRangeIteratorContent(ctx: ConcolicRunContext, range: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertypeSoft(ctx, ctx.typeSystem.pythonRangeIterator)
        val start = ctx.curState!!.memory.readField(range.address, RangeContents.start, intSort)
        ctx.curState!!.memory.writeField(address, RangeIteratorContents.start, intSort, start, trueExpr)
        val length = ctx.curState!!.memory.readField(range.address, RangeContents.length, intSort)
        ctx.curState!!.memory.writeField(address, RangeIteratorContents.length, intSort, length, trueExpr)
        val step = ctx.curState!!.memory.readField(range.address, RangeContents.step, intSort)
        ctx.curState!!.memory.writeField(address, RangeIteratorContents.step, intSort, step, trueExpr)
        val index = mkIntNum(0)
        ctx.curState!!.memory.writeField(address, RangeIteratorContents.index, intSort, index, trueExpr)
    }

    fun getRangeIteratorState(ctx: ConcolicRunContext): Pair<UExpr<KIntSort>, UExpr<KIntSort>> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
        val index = ctx.curState!!.memory.readField(address, RangeIteratorContents.index, intSort)
        val length = ctx.curState!!.memory.readField(address, RangeIteratorContents.length, intSort)
        return index to length
    }

    fun getRangeIteratorNext(ctx: ConcolicRunContext): UExpr<KIntSort> = with(ctx.ctx) {
        require(ctx.curState != null)
        addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
        val index = ctx.curState!!.memory.readField(address, RangeIteratorContents.index, intSort)
        val newIndex = mkArithAdd(index, mkIntNum(1))
        ctx.curState!!.memory.writeField(address, RangeIteratorContents.index, intSort, newIndex, trueExpr)
        val start = ctx.curState!!.memory.readField(address, RangeIteratorContents.start, intSort)
        val step = ctx.curState!!.memory.readField(address, RangeIteratorContents.step, intSort)
        return mkArithAdd(start, mkArithMul(index, step))
    }

    fun getTypeIfDefined(ctx: ConcolicRunContext): PythonType? {
        val interpreted = interpretSymbolicPythonObject(this, ctx.modelHolder)
        return interpreted.getConcreteType(ctx)
    }

    private fun resolvesToNullInCurrentModel(ctx: ConcolicRunContext): Boolean {
        val interpreted = interpretSymbolicPythonObject(this, ctx.modelHolder)
        return interpreted.address.address == 0
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
    abstract fun getTypeStream(ctx: ConcolicRunContext): UTypeStream<PythonType>?
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
    override fun getTypeStream(ctx: ConcolicRunContext): UTypeStream<PythonType>? = getTypeStream()

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
        return ctx.curState!!.memory.readField(address, BoolContents.content, ctx.ctx.boolSort) as KInterpretedValue<KBoolSort>
    }

    override fun getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> {
        require(ctx.curState != null)
        return ctx.curState!!.memory.readField(address, IntContents.content, ctx.ctx.intSort) as KInterpretedValue<KIntSort>
    }

    override fun getTypeStream(ctx: ConcolicRunContext): UTypeStream<PythonType> {
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