package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.*
import org.usvm.language.types.MockType
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.model.PyModel
import org.usvm.machine.model.constructModelWithNewMockEvaluator
import org.usvm.machine.model.substituteModel
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.symbolicobjects.memory.getBoolContent
import org.usvm.machine.symbolicobjects.memory.getIntContent
import org.usvm.machine.utils.symbolIsMocked

fun virtualNbBoolKt(ctx: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    ctx.curState ?: throw UnregisteredVirtualOperation
    ctx.curOperation ?: throw UnregisteredVirtualOperation
    val interpretedArg = interpretSymbolicPythonObject(ctx, ctx.curOperation!!.args.first())
    if (ctx.curOperation?.method != NbBoolMethod || interpretedArg.address.address != on.interpretedObjRef)
        throw UnregisteredVirtualOperation  // path diversion

    val oldModel = ctx.modelHolder.model
    val (interpretedObj, _) = internalVirtualCallKt(ctx) { mockSymbol ->
        val trueObject = ctx.modelHolder.model.eval(ctx.curState!!.preAllocatedObjects.trueObject.address)
        val falseObject = ctx.modelHolder.model.eval(ctx.curState!!.preAllocatedObjects.falseObject.address)
        listOf(
            constructModelWithNewMockEvaluator(
                ctx.ctx,
                oldModel,
                mockSymbol,
                ctx.curState!!.pathConstraints,  // one constraint will be missing (TODO: is it ok?)
                trueObject as UConcreteHeapRef,
                useOldPossibleRefs = true
            ),
            constructModelWithNewMockEvaluator(
                ctx.ctx,
                oldModel,
                mockSymbol,
                ctx.curState!!.pathConstraints,  // one constraint will be missing (TODO: is it ok?)
                falseObject as UConcreteHeapRef,
                useOldPossibleRefs = true
            )
        )
    }

    return interpretedObj.getBoolContent(ctx).isTrue
}

fun virtualSqLengthKt(ctx: ConcolicRunContext, on: VirtualPythonObject): Int = with(ctx.ctx) {
    ctx.curState ?: throw UnregisteredVirtualOperation
    ctx.curOperation ?: throw UnregisteredVirtualOperation
    val typeSystem = ctx.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(ctx, ctx.curOperation!!.args.first())
    require(ctx.curOperation?.method == SqLengthMethod && interpretedArg.address.address == on.interpretedObjRef)
    val (interpretedObj, symbolic) = internalVirtualCallKt(ctx)
    symbolic.addSupertypeSoft(ctx, typeSystem.pythonInt)
    val intValue = interpretedObj.getIntContent(ctx)
    myAssert(ctx, intValue ge mkIntNum(0))
    myAssert(ctx, intValue le mkIntNum(Int.MAX_VALUE))
    return intValue.toString().toInt()
}

private fun internalVirtualCallKt(
    ctx: ConcolicRunContext,
    customNewModelsCreation: (UMockSymbol<UAddressSort>) -> List<Pair<PyModel, UBoolExpr>> = { emptyList() }
): Pair<InterpretedSymbolicPythonObject, UninterpretedSymbolicPythonObject> = with(ctx.ctx) {
    ctx.curOperation ?: throw UnregisteredVirtualOperation
    ctx.curState ?: throw UnregisteredVirtualOperation
    val owner = ctx.curOperation.methodOwner ?: throw UnregisteredVirtualOperation
    val ownerIsAlreadyMocked = symbolIsMocked(owner, ctx)
    if (ownerIsAlreadyMocked) {
        owner.addSupertypeSoft(ctx, MockType)
    }
    var clonedState = if (!ownerIsAlreadyMocked) ctx.curState!!.clone() else null
    if (clonedState != null) {
        clonedState = myAssertOnState(clonedState, mkHeapRefEq(owner.address, nullRef).not())
    }
    val (symbolic, isNew, mockSymbol) = ctx.curState!!.mock(ctx.curOperation)
    if (!ownerIsAlreadyMocked && clonedState != null) {
        addDelayedFork(ctx, owner, clonedState, null)
    }
    if (ctx.curOperation.method.isMethodWithNonVirtualReturn && isNew) {
        val customNewModels = customNewModelsCreation(mockSymbol)
        val (newModel, constraint) =
            if (customNewModels.isEmpty())
                constructModelWithNewMockEvaluator(
                    ctx.ctx,
                    ctx.modelHolder.model,
                    mockSymbol,
                    ctx.curState!!.pathConstraints,  // one constraint will be missing (TODO: is it ok?)
                    useOldPossibleRefs = true
                )
            else
                customNewModels.first()

        customNewModels.drop(1).forEach { (nextNewModel, constraint) ->
            val newState = ctx.curState!!.clone()
            newState.models = listOf(nextNewModel)
            newState.pathConstraints += constraint
            ctx.forkedStates.add(newState)
        }

        substituteModel(ctx.curState!!, newModel, constraint, ctx)
    }
    val concrete = interpretSymbolicPythonObject(ctx, symbolic)
    return concrete to symbolic
}

fun virtualCallKt(ctx: ConcolicRunContext): PyObject {
    ctx.curState ?: throw UnregisteredVirtualOperation
    val (interpreted, _) = internalVirtualCallKt(ctx)
    val objectModel = ctx.builder!!.convert(interpreted)
    return ctx.renderer!!.convert(objectModel)
}

fun virtualCallSymbolKt(ctx: ConcolicRunContext): UninterpretedSymbolicPythonObject {
    ctx.curState ?: throw UnregisteredVirtualOperation
    val result = internalVirtualCallKt(ctx).second
    if (!ctx.curOperation!!.method.isMethodWithNonVirtualReturn) {
        val softConstraint = ctx.ctx.mkHeapRefEq(result.address, ctx.ctx.nullRef)
        val ps = ctx.curState!!.pathConstraints
        ps.pythonSoftConstraints = ps.pythonSoftConstraints.add(softConstraint)
    }
    return result
}

object UnregisteredVirtualOperation: Exception() {
    private fun readResolve(): Any = UnregisteredVirtualOperation
}