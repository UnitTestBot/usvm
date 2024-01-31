package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.*
import org.usvm.constraints.UTypeConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PyCallable
import org.usvm.language.types.*
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.interpreters.symbolic.operations.basic.myAssert
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.model.getConcreteType
import org.usvm.machine.model.getFirstType
import org.usvm.machine.model.isAlreadyMocked
import org.usvm.memory.UMemory
import org.usvm.types.TypesResult
import org.usvm.types.USingleTypeStream
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
        ctx: PyContext,
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
        ctx: PyContext,
        typeConstraints: UTypeConstraints<PythonType>,
        type: PythonType
    ): UBoolExpr {
        var result: UBoolExpr = typeConstraints.evalIsSubtype(address, type)
        if (typeSystem.isNonNullType(type))
            result = with(ctx) { result and mkHeapRefEq(address, nullRef).not() }
        return result
    }

    fun getTypeIfDefined(ctx: ConcolicRunContext): PythonType? {
        val interpreted = interpretSymbolicPythonObject(ctx, this)
        return interpreted.getConcreteType()
    }

    fun getTypeIfDefined(state: PyState): PythonType? {
        val holder = PyModelHolder(state.pyModel)
        val interpreted = interpretSymbolicPythonObject(holder, state.memory, this)
        return interpreted.getConcreteType()
    }

    fun getFirstTypeIfDefined(ctx: ConcolicRunContext): PythonType? {
        val interpreted = interpretSymbolicPythonObject(ctx, this)
        return interpreted.getFirstType()
    }

    fun isAlreadyMocked(ctx: ConcolicRunContext): Boolean {
        val interpreted = interpretSymbolicPythonObject(ctx, this)
        return interpreted.isAlreadyMocked()
    }

    private fun resolvesToNullInCurrentModel(ctx: ConcolicRunContext): Boolean {
        val interpreted = interpretSymbolicPythonObject(ctx, this)
        return interpreted.address.address == 0
    }

    fun getTimeOfCreation(ctx: ConcolicRunContext): UExpr<KIntSort> {  // must not be called on nullref
        require(ctx.curState != null)
        return getTimeOfCreation(ctx.curState!!)
    }

    fun getTimeOfCreation(state: PyState): UExpr<KIntSort> {  // must not be called on nullref
        return state.memory.readField(address, TimeOfCreation, state.ctx.intSort)
    }

    fun setMinimalTimeOfCreation(ctx: PyContext, memory: UMemory<PythonType, PyCallable>) {  // must not be called on nullref
        memory.writeField(address, TimeOfCreation, ctx.intSort, ctx.mkIntNum(-1_000_000_000), ctx.trueExpr)
    }

    fun isAllocatedObject(ctx: ConcolicRunContext): Boolean {
        val evaluated = ctx.modelHolder.model.eval(address) as UConcreteHeapRef
        return evaluated.address > 0
    }
}

sealed class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    typeSystem: PythonTypeSystem
): SymbolicPythonObject(address, typeSystem) {
    abstract fun getConcreteType(): ConcretePythonType?
    abstract fun getFirstType(): PythonType?
    abstract fun getTypeStream(): UTypeStream<PythonType>?
    abstract fun isAlreadyMocked(): Boolean
}

class InterpretedInputSymbolicPythonObject(
    address: UConcreteHeapRef,
    val modelHolder: PyModelHolder,
    typeSystem: PythonTypeSystem
): InterpretedSymbolicPythonObject(address, typeSystem) {
    init {
        require(!isStaticHeapRef(address) && !isAllocatedConcreteHeapRef(address))
    }
    override fun getFirstType(): PythonType? {
        if (address.address == 0)
            return MockType
        return modelHolder.model.getFirstType(address)
    }

    override fun getConcreteType(): ConcretePythonType? {
        if (address.address == 0)
            return null
        return modelHolder.model.getConcreteType(address)
    }

    override fun getTypeStream(): UTypeStream<PythonType>? {
        if (address.address == 0)
            return null
        return modelHolder.model.typeStreamOf(address)
    }

    override fun isAlreadyMocked(): Boolean {
        if (address.address == 0)
            return false  // TODO
        return modelHolder.model.isAlreadyMocked(address)
    }
}

class InterpretedAllocatedOrStaticSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    val type: ConcretePythonType,
    typeSystem: PythonTypeSystem
): InterpretedSymbolicPythonObject(address, typeSystem) {
    init {
        require(isAllocatedConcreteHeapRef(address) || isStaticHeapRef(address))
    }
    override fun getConcreteType(): ConcretePythonType = type

    override fun getFirstType(): PythonType = type

    override fun getTypeStream(): UTypeStream<PythonType> = USingleTypeStream(typeSystem, type)

    override fun isAlreadyMocked(): Boolean = false
}

fun interpretSymbolicPythonObject(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject
): InterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    return interpretSymbolicPythonObject(ctx.modelHolder, ctx.curState!!.memory, obj)
}

fun interpretSymbolicPythonObject(
    modelHolder: PyModelHolder,
    memory: UMemory<PythonType, PyCallable>,
    obj: UninterpretedSymbolicPythonObject
): InterpretedSymbolicPythonObject {
    val evaluated = modelHolder.model.eval(obj.address) as UConcreteHeapRef
    if (isAllocatedConcreteHeapRef(evaluated) || isStaticHeapRef(evaluated)) {
        val typeStream = memory.typeStreamOf(evaluated)
        val type = typeStream.first()
        val taken = typeStream.take(2)
        require(taken is TypesResult.SuccessfulTypesResult && taken.types.size == 1 && type is ConcretePythonType) {
            "Static and allocated objects must have concrete types"
        }
        return InterpretedAllocatedOrStaticSymbolicPythonObject(evaluated, type, obj.typeSystem)
    }
    return InterpretedInputSymbolicPythonObject(evaluated, modelHolder, obj.typeSystem)
}