package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.*
import org.usvm.constraints.UTypeConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonCallable
import org.usvm.language.TimeOfCreation
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.interpreters.operations.myAssert
import org.usvm.language.types.*
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemory
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

    fun getTypeIfDefined(ctx: ConcolicRunContext): PythonType? {
        val interpreted = interpretSymbolicPythonObject(this, ctx.modelHolder)
        return interpreted.getConcreteType(ctx)
    }

    private fun resolvesToNullInCurrentModel(ctx: ConcolicRunContext): Boolean {
        val interpreted = interpretSymbolicPythonObject(this, ctx.modelHolder)
        return interpreted.address.address == 0
    }

    fun getTimeOfCreation(ctx: ConcolicRunContext): UExpr<KIntSort> {  // must not be called on nullref
        require(ctx.curState != null)
        return ctx.curState!!.memory.readField(address, TimeOfCreation, ctx.ctx.intSort)
    }

    fun setMinimalTimeOfCreation(ctx: UPythonContext, memory: UMemory<PythonType, PythonCallable>) {  // must not be called on nullref
        memory.writeField(address, TimeOfCreation, ctx.intSort, ctx.mkIntNum(-1_000_000_000), ctx.trueExpr)
    }

    fun readElement(ctx: ConcolicRunContext, index: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
        require(ctx.curState != null)
        val type = getTypeIfDefined(ctx)
        require(type != null && type is ArrayLikeConcretePythonType)
        val elemAddress = ctx.curState!!.memory.readArrayIndex(address, index, ArrayType, ctx.ctx.addressSort)
        val elem = UninterpretedSymbolicPythonObject(elemAddress, typeSystem)
        if (isAllocatedObject(ctx))
            return elem
        val cond = type.elementConstraints.fold(ctx.ctx.trueExpr as UBoolExpr) { acc, constraint ->
            ctx.ctx.mkAnd(acc, constraint.applyUninterpreted(this, elem, ctx))
        }
        myAssert(ctx, cond)
        return elem
    }

    fun writeElement(ctx: ConcolicRunContext, index: UExpr<KIntSort>, value: UninterpretedSymbolicPythonObject) {
        require(ctx.curState != null)
        val type = getTypeIfDefined(ctx)
        require(type != null && type is ArrayLikeConcretePythonType)
        val cond = type.elementConstraints.fold(ctx.ctx.trueExpr as UBoolExpr) { acc, constraint ->
            ctx.ctx.mkAnd(acc, constraint.applyUninterpreted(this, value, ctx))
        }
        myAssert(ctx, cond)
        ctx.curState!!.memory.writeArrayIndex(address, index, ArrayType, ctx.ctx.addressSort, value.address, ctx.ctx.trueExpr)
    }

    fun extendConstraints(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
        require(ctx.curState != null)
        val type = getTypeIfDefined(ctx)
        require(type != null && type is ArrayLikeConcretePythonType)
        type.elementConstraints.forEach {  constraint ->
            on.addSupertypeSoft(ctx, HasElementConstraint(constraint))
        }
    }

    private fun isAllocatedObject(ctx: ConcolicRunContext): Boolean {
        val evaluated = ctx.modelHolder.model.eval(address) as UConcreteHeapRef
        return evaluated.address > 0
    }
}

sealed class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    typeSystem: PythonTypeSystem
): SymbolicPythonObject(address, typeSystem) {
    abstract fun getConcreteType(ctx: ConcolicRunContext): ConcretePythonType?
    abstract fun getFirstType(ctx: ConcolicRunContext): PythonType?
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