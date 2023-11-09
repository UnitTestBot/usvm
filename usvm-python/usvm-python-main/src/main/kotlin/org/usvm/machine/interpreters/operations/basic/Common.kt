package org.usvm.machine.interpreters.operations.basic

import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.allocateArrayInitialized
import org.usvm.api.writeArrayLength
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.language.SymbolForCPython
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.language.types.ArrayType
import org.usvm.language.types.ConcretePythonType
import org.usvm.machine.interpreters.PythonObject
import org.usvm.language.types.ConcreteTypeNegation
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.utils.MethodDescription

fun handlerIsinstanceKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject, typeRef: PythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    val type = typeSystem.concreteTypeOnAddress(typeRef) ?: return null
    if (type == typeSystem.pythonObjectType)
        return constructBool(ctx, ctx.ctx.trueExpr)

    val interpreted = interpretSymbolicPythonObject(ctx, obj)
    val concreteType = interpreted.getConcreteType()
    return if (concreteType == null) {
        if (type == typeSystem.pythonInt) {  //  this is a common case, TODO: better solution
            val cond =
                obj.evalIs(ctx, ConcreteTypeNegation(typeSystem.pythonInt)) and obj.evalIs(ctx, ConcreteTypeNegation(typeSystem.pythonBool))
            myFork(ctx, cond)
        } else {
            myFork(ctx, obj.evalIs(ctx, type))
        }
        require(interpreted.getConcreteType() == null)
        constructBool(ctx, falseExpr)
    } else {
        if (type == typeSystem.pythonInt) {  //  this is a common case
            myAssert(ctx, obj.evalIs(ctx, typeSystem.pythonBool).not())  // to avoid underapproximation
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

fun handlerAndKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    left.addSupertype(ctx, typeSystem.pythonBool)
    right.addSupertype(ctx, typeSystem.pythonBool)
    val leftValue = left.getBoolContent(ctx)
    val rightValue = right.getBoolContent(ctx)
    return constructBool(ctx, mkAnd(leftValue, rightValue))
}

fun lostSymbolicValueKt(ctx: ConcolicRunContext, description: String) {
    if (ctx.curState != null)
        ctx.statistics.addLostSymbolicValue(MethodDescription(description))
}

fun createIterable(
    ctx: ConcolicRunContext,
    elements: List<UninterpretedSymbolicPythonObject>,
    type: ConcretePythonType
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val addresses = elements.map { it.address }.asSequence()
    val typeSystem = ctx.typeSystem
    val size = elements.size
    with (ctx.ctx) {
        val iterableAddress = ctx.curState!!.memory.allocateArrayInitialized(ArrayType, addressSort, intSort, addresses)
        ctx.curState!!.memory.writeArrayLength(iterableAddress, mkIntNum(size), ArrayType, intSort)
        ctx.curState!!.memory.types.allocate(iterableAddress.address, type)
        val result = UninterpretedSymbolicPythonObject(iterableAddress, typeSystem)
        result.addSupertypeSoft(ctx, type)
        return result
    }
}

fun handlerIsOpKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    val leftType = left.getTypeIfDefined(ctx)
    val rightType = right.getTypeIfDefined(ctx)
    if (leftType == null || rightType == null) {
        myFork(ctx, mkHeapRefEq(left.address, right.address))
    }
    if (leftType != rightType)
        return
    when (leftType) {
        ctx.typeSystem.pythonBool ->
            myFork(ctx, left.getBoolContent(ctx) xor right.getBoolContent(ctx))
        ctx.typeSystem.pythonInt ->
            myFork(ctx, left.getIntContent(ctx) eq right.getIntContent(ctx))
        else ->
            myFork(ctx, mkHeapRefEq(left.address, right.address))
    }
}

fun handlerNoneCheckKt(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    myFork(ctx, on.evalIs(ctx, ctx.typeSystem.pythonNoneType))
}

fun handlerStandardTpGetattroKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject
): SymbolForCPython? {
    if (ctx.curState == null)
        return null
    val concreteStr = ctx.curState!!.preAllocatedObjects.concreteString(name) ?: return null
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
    if (!ConcretePythonInterpreter.typeHasStandardDict(type.asObject))
        return null
    val containsFieldCond = obj.containsField(ctx, name)
    if (ctx.modelHolder.model.eval(containsFieldCond).isFalse) {
        if (defaultValue != null)
            return SymbolForCPython(defaultValue, 0)
        myFork(ctx, containsFieldCond)
        return null
    } else {
        myAssert(ctx, containsFieldCond)
    }
    return SymbolForCPython(obj.getFieldValue(ctx, name), 0)
}

fun handlerStandardTpSetattroKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    val concreteStr = ctx.curState!!.preAllocatedObjects.concreteString(name) ?: return
    val type = obj.getTypeIfDefined(ctx) as? ConcretePythonType ?: return
    if (!ConcretePythonInterpreter.typeHasStandardDict(type.asObject))
        return
    val descriptor = ConcretePythonInterpreter.typeLookup(type.asObject, concreteStr)
    if (descriptor != null) {
        val descrType = ConcretePythonInterpreter.getPythonObjectType(descriptor)
        if (ConcretePythonInterpreter.typeHasTpDescrSet(descrType))
            return
    }
    obj.setFieldValue(ctx, name, value)
}

fun getArraySize(context: ConcolicRunContext, array: UninterpretedSymbolicPythonObject, type: ArrayLikeConcretePythonType): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    if (array.getTypeIfDefined(context) != type)
        return null
    val listSize = array.readArrayLength(context)
    return constructInt(context, listSize)
}


fun resolveSequenceIndex(
    ctx: ConcolicRunContext,
    seq: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
    type: ArrayLikeConcretePythonType
): UExpr<KIntSort>? {
    if (ctx.curState == null)
        return null
    with (ctx.ctx) {
        val typeSystem = ctx.typeSystem
        index.addSupertypeSoft(ctx, typeSystem.pythonInt)
        seq.addSupertypeSoft(ctx, type)

        val listSize = seq.readArrayLength(ctx)
        val indexValue = index.getIntContent(ctx)

        val indexCond = mkAnd(indexValue lt listSize, mkArithUnaryMinus(listSize) le indexValue)
        myFork(ctx, indexCond)

        if (ctx.curState!!.pyModel.eval(indexCond).isFalse)
            return null

        val positiveIndex = mkAnd(indexValue lt listSize, mkIntNum(0) le indexValue)
        myFork(ctx, positiveIndex)

        return if (ctx.curState!!.pyModel.eval(positiveIndex).isTrue) {
            indexValue
        } else {
            val negativeIndex = mkAnd(indexValue lt mkIntNum(0), mkArithUnaryMinus(listSize) le indexValue)
            require(ctx.curState!!.pyModel.eval(negativeIndex).isTrue)
            mkArithAdd(indexValue, listSize)
        }
    }
}

fun handlerCreateEmptyObjectKt(
    ctx: ConcolicRunContext,
    typeRef: PythonObject
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val typeSystem = ctx.typeSystem
    val type = typeSystem.concreteTypeOnAddress(typeRef) ?: return null
    return constructEmptyObject(ctx.ctx, ctx.curState!!.memory, ctx.typeSystem, type)
}