package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.allocateArrayInitialized
import org.usvm.api.writeArrayLength
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.interpreters.symbolic.operations.nativecalls.addConstraintsFromNativeId
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructEmptyAllocatedObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.containsField
import org.usvm.machine.symbolicobjects.memory.getBoolContent
import org.usvm.machine.symbolicobjects.memory.getFieldValue
import org.usvm.machine.symbolicobjects.memory.getIntContent
import org.usvm.machine.symbolicobjects.memory.readArrayLength
import org.usvm.machine.symbolicobjects.memory.setFieldValue
import org.usvm.machine.types.ArrayLikeConcretePythonType
import org.usvm.machine.types.ArrayType
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.ConcreteTypeNegation
import org.usvm.machine.types.HasTpHash
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.types.getTypeFromTypeHint
import org.usvm.machine.utils.MethodDescription
import org.utpython.types.getPythonAttributeByName
import java.util.stream.Stream
import kotlin.streams.asSequence

/**
 * Corresponds to Python's `isinstance` function.
 * Right now, it is a pretty rough approximation of the behavior.
 * We do not fully support Python's internal subtyping.
 *
 * A common case that is here roughly implemented is `int` and `bool`.
 * In Python, `bool` is a subclass of `int`.
 * */
fun handlerIsinstanceKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
    typeRef: PyObject,
): UninterpretedSymbolicPythonObject? = with(
    ctx.ctx
) {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    val type = typeSystem.concreteTypeOnAddress(typeRef) ?: return null
    if (type == typeSystem.pythonObjectType) {
        return constructBool(ctx, ctx.ctx.trueExpr)
    }

    val interpreted = interpretSymbolicPythonObject(ctx, obj)
    val concreteType = interpreted.getConcreteType()
    return if (concreteType == null) {
        if (type == typeSystem.pythonInt) { //  this is a common case, TODO: better solution
            val cond =
                obj.evalIs(
                    ctx,
                    ConcreteTypeNegation(typeSystem.pythonInt)
                ) and obj.evalIs(
                    ctx, ConcreteTypeNegation(typeSystem.pythonBool)
                )
            pyFork(ctx, cond)
        } else {
            pyFork(ctx, obj.evalIs(ctx, type))
        }
        require(interpreted.getConcreteType() == null)
        constructBool(ctx, falseExpr)
    } else {
        if (type == typeSystem.pythonInt) { //  this is a common case
            pyAssert(ctx, obj.evalIs(ctx, typeSystem.pythonBool).not()) // to avoid underapproximation
            constructBool(ctx, obj.evalIs(ctx, typeSystem.pythonInt))
        } else {
            constructBool(ctx, obj.evalIs(ctx, type))
        }
    }
}

fun fixateTypeKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject) {
    ctx.curState ?: return
    val interpreted = interpretSymbolicPythonObject(ctx, obj)
    val type = interpreted.getConcreteType() ?: return
    obj.addSupertype(ctx, type)
}

fun handlerAndKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? = with(
    ctx.ctx
) {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    left.addSupertype(ctx, typeSystem.pythonBool)
    right.addSupertype(ctx, typeSystem.pythonBool)
    val leftValue = left.getBoolContent(ctx)
    val rightValue = right.getBoolContent(ctx)
    return constructBool(ctx, mkAnd(leftValue, rightValue))
}

fun lostSymbolicValueKt(ctx: ConcolicRunContext, description: String) {
    if (ctx.curState != null) {
        ctx.statistics.addLostSymbolicValue(MethodDescription(description))
    }
}

fun createIterable(
    ctx: ConcolicRunContext,
    elements: List<UninterpretedSymbolicPythonObject>,
    type: ConcretePythonType,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val addresses = elements.map { it.address }.asSequence()
    val typeSystem = ctx.typeSystem
    val size = elements.size
    with(ctx.ctx) {
        val iterableAddress = ctx.extractCurState()
            .memory
            .allocateArrayInitialized(ArrayType, addressSort, intSort, addresses)
        ctx.extractCurState().memory.writeArrayLength(iterableAddress, mkIntNum(size), ArrayType, intSort)
        ctx.extractCurState().memory.types.allocate(iterableAddress.address, type)
        val result = UninterpretedSymbolicPythonObject(iterableAddress, typeSystem)
        result.addSupertypeSoft(ctx, type)
        return result
    }
}

fun handlerStrEqKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    left.addSupertype(ctx, ctx.typeSystem.pythonStr)
    right.addSupertype(ctx, ctx.typeSystem.pythonStr)
    val result = ctx.ctx.mkHeapRefEq(left.address, right.address)
    return constructBool(ctx, result)
}

fun handlerStrNeqKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    left.addSupertype(ctx, ctx.typeSystem.pythonStr)
    right.addSupertype(ctx, ctx.typeSystem.pythonStr)
    val result = ctx.ctx.mkNot(ctx.ctx.mkHeapRefEq(left.address, right.address))
    return constructBool(ctx, result)
}

fun handlerIsOpKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
) = with(ctx.ctx) {
    ctx.curState ?: return
    val leftType = left.getTypeIfDefined(ctx)
    val rightType = right.getTypeIfDefined(ctx)
    if (leftType != null && rightType == null) {
        pyFork(ctx, right.evalIs(ctx, leftType))
    } else if (rightType != null && leftType == null) {
        pyFork(ctx, left.evalIs(ctx, rightType))
    }
    if (leftType != rightType) {
        pyFork(ctx, mkHeapRefEq(left.address, right.address))
        return
    }
    when (leftType) {
        ctx.typeSystem.pythonBool ->
            pyFork(ctx, left.getBoolContent(ctx) xor right.getBoolContent(ctx))

        ctx.typeSystem.pythonInt ->
            pyFork(ctx, left.getIntContent(ctx) eq right.getIntContent(ctx))

        ctx.typeSystem.pythonNoneType ->
            return

        else ->
            pyFork(ctx, mkHeapRefEq(left.address, right.address))
    }
}

fun handlerNoneCheckKt(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    pyFork(ctx, on.evalIs(ctx, ctx.typeSystem.pythonNoneType))
}

fun handlerStandardTpGetattroKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject,
): SymbolForCPython? {
    if (ctx.curState == null) {
        return null
    }
    val concreteStr = ctx.extractCurState().preAllocatedObjects.concreteString(name) ?: return null
    val type = obj.getTypeIfDefined(ctx) as? ConcretePythonType ?: return null
    val concreteDescriptor = ConcretePythonInterpreter.typeLookup(type.asObject, concreteStr)
    var defaultValue: UninterpretedSymbolicPythonObject? = null
    if (concreteDescriptor != null) {
        val typeOfDescriptor = ConcretePythonInterpreter.getPythonObjectType(concreteDescriptor)
        if (ConcretePythonInterpreter.typeHasTpDescrGet(typeOfDescriptor)) {
            val memberDescriptor = ConcretePythonInterpreter.getSymbolicDescriptor(concreteDescriptor) ?: return null
            return memberDescriptor.getMember(ctx, obj)
        } else {
            defaultValue = handlerLoadConstKt(ctx, concreteDescriptor)
        }
    }
    if (!ConcretePythonInterpreter.typeHasStandardDict(type.asObject)) {
        return null
    }
    val containsFieldCond = obj.containsField(ctx, name)
    val result = obj.getFieldValue(ctx, name)

    val typeSystem = ctx.typeSystem
    val additionalCond: UBoolExpr = (typeSystem as? PythonTypeSystemWithMypyInfo)?.let {
        val utType = it.typeHintOfConcreteType(type) ?: return@let null
        val attrDef = utType.getPythonAttributeByName(it.typeHintsStorage, concreteStr) ?: return@let null
        val attrType = attrDef.type
        val concreteAttrType = getTypeFromTypeHint(attrType, it)
        result.evalIs(ctx, concreteAttrType)
    } ?: ctx.ctx.trueExpr

    if (ctx.modelHolder.model.eval(containsFieldCond).isFalse) {
        if (defaultValue != null) {
            return SymbolForCPython(defaultValue, 0)
        }
        pyFork(ctx, ctx.ctx.mkAnd(containsFieldCond, additionalCond))
        return null
    } else {
        pyAssert(ctx, containsFieldCond)
        pyAssert(ctx, additionalCond)
    }
    return SymbolForCPython(result, 0)
}

fun handlerStandardTpSetattroKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    val concreteStr = ctx.extractCurState().preAllocatedObjects.concreteString(name) ?: return
    val type = obj.getTypeIfDefined(ctx) as? ConcretePythonType ?: return
    if (!ConcretePythonInterpreter.typeHasStandardDict(type.asObject)) {
        return
    }
    val descriptor = ConcretePythonInterpreter.typeLookup(type.asObject, concreteStr)
    if (descriptor != null) {
        val descrType = ConcretePythonInterpreter.getPythonObjectType(descriptor)
        if (ConcretePythonInterpreter.typeHasTpDescrSet(descrType)) {
            return
        }
    }
    obj.setFieldValue(ctx, name, value)
}

fun getArraySize(
    context: ConcolicRunContext,
    array: UninterpretedSymbolicPythonObject,
    type: ArrayLikeConcretePythonType,
): UninterpretedSymbolicPythonObject? {
    if (context.curState == null) {
        return null
    }
    if (array.getTypeIfDefined(context) != type) {
        return null
    }
    val listSize = array.readArrayLength(context)
    return constructInt(context, listSize)
}


fun resolveSequenceIndex(
    ctx: ConcolicRunContext,
    seq: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
    type: ArrayLikeConcretePythonType,
): UExpr<KIntSort>? {
    if (ctx.curState == null) {
        return null
    }
    with(ctx.ctx) {
        val typeSystem = ctx.typeSystem
        index.addSupertypeSoft(ctx, typeSystem.pythonInt)
        seq.addSupertypeSoft(ctx, type)

        val listSize = seq.readArrayLength(ctx)
        val indexValue = index.getIntContent(ctx)

        val indexCond = mkAnd(indexValue lt listSize, mkArithUnaryMinus(listSize) le indexValue)
        pyFork(ctx, indexCond)

        if (ctx.extractCurState().pyModel.eval(indexCond).isFalse) {
            return null
        }

        val positiveIndex = mkAnd(indexValue lt listSize, mkIntNum(0) le indexValue)
        pyFork(ctx, positiveIndex)

        return if (ctx.extractCurState().pyModel.eval(positiveIndex).isTrue) {
            indexValue
        } else {
            val negativeIndex = mkAnd(indexValue lt mkIntNum(0), mkArithUnaryMinus(listSize) le indexValue)
            require(ctx.extractCurState().pyModel.eval(negativeIndex).isTrue)
            mkArithAdd(indexValue, listSize)
        }
    }
}

fun handlerCreateEmptyObjectKt(
    ctx: ConcolicRunContext,
    typeRef: PyObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    val type = typeSystem.concreteTypeOnAddress(typeRef) ?: return null
    return constructEmptyAllocatedObject(ctx.ctx, ctx.extractCurState().memory, ctx.typeSystem, type)
}

fun addHashableTypeConstrains(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject,
) = with(ctx.ctx) {
    var cond: UBoolExpr = trueExpr
    cond = cond and key.evalIsSoft(ctx, HasTpHash)
    cond = cond and key.evalIs(ctx, ctx.typeSystem.pythonList).not()
    cond = cond and key.evalIs(ctx, ctx.typeSystem.pythonDict).not()
    cond = cond and key.evalIs(ctx, ctx.typeSystem.pythonSet).not()
    pyAssert(ctx, cond)
}

fun forkOnUnknownHashableType(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject,
) = with(ctx.ctx) {
    require(key.getTypeIfDefined(ctx) == null)
    val keyIsInt = key.evalIs(ctx, ctx.typeSystem.pythonInt)
    val keyIsBool = key.evalIs(ctx, ctx.typeSystem.pythonBool)
    val keyIsFloat = key.evalIs(ctx, ctx.typeSystem.pythonFloat)
    val keyIsNone = key.evalIs(ctx, ctx.typeSystem.pythonNoneType)
    require(ctx.modelHolder.model.eval(keyIsInt or keyIsBool).isFalse)
    pyFork(ctx, keyIsInt)
    pyFork(ctx, keyIsBool)
    require(ctx.modelHolder.model.eval(keyIsFloat or keyIsNone).isFalse)
    pyAssert(ctx, (keyIsFloat or keyIsNone).not())
}

fun handlerCallOnKt(
    ctx: ConcolicRunContext,
    function: PyObject,
    args: Stream<UninterpretedSymbolicPythonObject>,
) {
    ctx.curState ?: return
    addConstraintsFromNativeId(ctx, function, args.asSequence().toList())
}
