package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.typeStreamOf
import org.usvm.api.writeField
import org.usvm.constraints.UTypeConstraints
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.isTrue
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.symbolic.operations.basic.pyAssert
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.model.getConcreteType
import org.usvm.machine.model.getFirstType
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.ConcreteTypeNegation
import org.usvm.machine.types.MockType
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.memory.UMemory
import org.usvm.types.TypesResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.first

sealed class SymbolicPythonObject(
    open val address: UHeapRef,
    val typeSystem: PythonTypeSystem,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is SymbolicPythonObject) {
            return false
        }
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}

class UninterpretedSymbolicPythonObject(
    address: UHeapRef,
    typeSystem: PythonTypeSystem,
) : SymbolicPythonObject(address, typeSystem) {
    fun addSupertype(ctx: ConcolicRunContext, type: PythonType) {
        if (address is UConcreteHeapRef) {
            return
        }
        requireNotNull(ctx.curState)
        pyAssert(ctx, evalIs(ctx, type))
    }

    fun addSupertypeSoft(ctx: ConcolicRunContext, type: PythonType) {
        if (address is UConcreteHeapRef) {
            return
        }
        requireNotNull(ctx.curState)
        pyAssert(ctx, evalIsSoft(ctx, type))
    }

    fun evalIs(ctx: ConcolicRunContext, type: PythonType): UBoolExpr {
        requireNotNull(ctx.curState)
        val result = evalIs(ctx.ctx, ctx.extractCurState().pathConstraints.typeConstraints, type)
        if (resolvesToNullInCurrentModel(ctx) && ctx.extractCurState().pyModel.eval(result).isTrue) {
            ctx.extractCurState().possibleTypesForNull =
                ctx.extractCurState().possibleTypesForNull.filterBySupertype(type)
        }
        return result
    }

    fun evalIs(
        ctx: PyContext,
        typeConstraints: UTypeConstraints<PythonType>,
        type: PythonType,
    ): UBoolExpr {
        if (type is ConcretePythonType) {
            return with(ctx) {
                typeConstraints.evalIsSubtype(address, ConcreteTypeNegation(type)).not()
            }
        }
        return typeConstraints.evalIsSubtype(address, type)
    }

    fun evalIsSoft(ctx: ConcolicRunContext, type: PythonType): UBoolExpr {
        requireNotNull(ctx.curState)
        return evalIsSoft(ctx.ctx, ctx.extractCurState().pathConstraints.typeConstraints, type)
    }

    fun evalIsSoft(
        ctx: PyContext,
        typeConstraints: UTypeConstraints<PythonType>,
        type: PythonType,
    ): UBoolExpr {
        var result: UBoolExpr = typeConstraints.evalIsSubtype(address, type)
        if (type is ConcretePythonType) {
            result = with(ctx) { result and mkHeapRefEq(address, nullRef).not() }
        }
        return result
    }

    fun getTypeIfDefined(ctx: ConcolicRunContext): PythonType? {
        val interpreted = interpretSymbolicPythonObject(ctx, this)
        return interpreted.getConcreteType()
    }

    fun getTypeIfDefined(
        modelHolder: PyModelHolder,
        memory: UMemory<PythonType, PyCallable>,
    ): PythonType? {
        val interpreted = interpretSymbolicPythonObject(modelHolder, memory, this)
        return interpreted.getConcreteType()
    }

    private fun resolvesToNullInCurrentModel(ctx: ConcolicRunContext): Boolean {
        val interpreted = interpretSymbolicPythonObject(ctx, this)
        return interpreted.address.address == 0
    }

    fun getTimeOfCreation(ctx: ConcolicRunContext): UExpr<KIntSort> { // must not be called on nullref
        requireNotNull(ctx.curState)
        return ctx.extractCurState().memory.readField(address, TimeOfCreation, ctx.ctx.intSort)
    }

    // must not be called on nullref
    fun setMinimalTimeOfCreation(
        ctx: PyContext,
        memory: UMemory<PythonType, PyCallable>,
    ) {
        memory.writeField(address, TimeOfCreation, ctx.intSort, ctx.mkIntNum(-INF), ctx.trueExpr)
    }

    fun isAllocatedObject(ctx: ConcolicRunContext): Boolean {
        val evaluated = ctx.modelHolder.model.eval(address) as UConcreteHeapRef
        return evaluated.address > 0
    }
}

private const val INF = 1_000_000_000

sealed class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    typeSystem: PythonTypeSystem,
) : SymbolicPythonObject(address, typeSystem) {
    abstract fun getConcreteType(): ConcretePythonType?
    abstract fun getFirstType(): PythonType?
    abstract fun getTypeStream(): UTypeStream<PythonType>?
}

class InterpretedInputSymbolicPythonObject(
    address: UConcreteHeapRef,
    val modelHolder: PyModelHolder,
    typeSystem: PythonTypeSystem,
) : InterpretedSymbolicPythonObject(address, typeSystem) {
    init {
        require(!isStaticHeapRef(address) && !isAllocatedConcreteHeapRef(address))
    }
    override fun getFirstType(): PythonType? {
        if (address.address == 0) {
            return MockType
        }
        return modelHolder.model.getFirstType(address)
    }

    override fun getConcreteType(): ConcretePythonType? {
        if (address.address == 0) {
            return null
        }
        return modelHolder.model.getConcreteType(address)
    }

    override fun getTypeStream(): UTypeStream<PythonType>? {
        if (address.address == 0) {
            return null
        }
        return modelHolder.model.typeStreamOf(address)
    }
}

class InterpretedAllocatedOrStaticSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    val type: ConcretePythonType,
    typeSystem: PythonTypeSystem,
) : InterpretedSymbolicPythonObject(address, typeSystem) {
    init {
        require(isAllocatedConcreteHeapRef(address) || isStaticHeapRef(address))
    }
    override fun getConcreteType(): ConcretePythonType = type

    override fun getFirstType(): PythonType = type

    override fun getTypeStream(): UTypeStream<PythonType> = USingleTypeStream(typeSystem, type)
}

fun interpretSymbolicPythonObject(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
): InterpretedSymbolicPythonObject {
    requireNotNull(ctx.curState)
    return interpretSymbolicPythonObject(ctx.modelHolder, ctx.extractCurState().memory, obj)
}

fun interpretSymbolicPythonObject(
    modelHolder: PyModelHolder,
    memory: UMemory<PythonType, PyCallable>,
    obj: UninterpretedSymbolicPythonObject,
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
