package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UMockSymbol
import org.usvm.isTrue
import org.usvm.language.NbBoolMethod
import org.usvm.language.SqLengthMethod
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.UnregisteredVirtualOperation
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.VirtualPythonObject
import org.usvm.machine.mock
import org.usvm.machine.model.PyModel
import org.usvm.machine.model.UseOldPathConstraintsInfo
import org.usvm.machine.model.constructModelWithNewMockEvaluator
import org.usvm.machine.model.substituteModel
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.getBoolContent
import org.usvm.machine.symbolicobjects.memory.getIntContent

fun virtualNbBoolKt(ctx: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    val curOperation = ctx.curOperation
    if (ctx.curState == null || curOperation == null) {
        throw UnregisteredVirtualOperation()
    }
    val interpretedArg = interpretSymbolicPythonObject(ctx, curOperation.args.first())
    if (curOperation.method != NbBoolMethod || interpretedArg.address.address != on.interpretedObjRef) {
        throw UnregisteredVirtualOperation() // path diversion
    }

    val oldModel = ctx.modelHolder.model
    val (interpretedObj, _) = internalVirtualCallKt(ctx) { mockSymbol ->
        val trueObject = ctx.modelHolder.model.eval(ctx.extractCurState().preAllocatedObjects.trueObject.address)
        val falseObject = ctx.modelHolder.model.eval(ctx.extractCurState().preAllocatedObjects.falseObject.address)
        listOf(
            constructModelWithNewMockEvaluator(
                ctx.ctx,
                oldModel,
                mockSymbol,
                UseOldPathConstraintsInfo(oldModel.psInfo),
                trueObject as UConcreteHeapRef,
            ),
            constructModelWithNewMockEvaluator(
                ctx.ctx,
                oldModel,
                mockSymbol,
                UseOldPathConstraintsInfo(oldModel.psInfo),
                falseObject as UConcreteHeapRef,
            )
        )
    }

    return interpretedObj.getBoolContent(ctx).isTrue
}

fun virtualSqLengthKt(ctx: ConcolicRunContext, on: VirtualPythonObject): Int = with(ctx.ctx) {
    val curOperation = ctx.curOperation
    if (ctx.curState == null || curOperation == null) {
        throw UnregisteredVirtualOperation()
    }
    val typeSystem = ctx.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(ctx, curOperation.args.first())
    require(curOperation.method == SqLengthMethod && interpretedArg.address.address == on.interpretedObjRef)
    val (interpretedObj, symbolic) = internalVirtualCallKt(ctx)
    symbolic.addSupertypeSoft(ctx, typeSystem.pythonInt)
    val intValue = interpretedObj.getIntContent(ctx)
    pyAssert(ctx, intValue ge mkIntNum(0))
    pyAssert(ctx, intValue le mkIntNum(Int.MAX_VALUE))
    return intValue.toString().toInt()
}

private fun internalVirtualCallKt(
    ctx: ConcolicRunContext,
    customNewModelsCreation: (UMockSymbol<UAddressSort>) -> List<Pair<PyModel, UBoolExpr>> = { emptyList() },
): Pair<InterpretedSymbolicPythonObject, UninterpretedSymbolicPythonObject> = with(ctx.ctx) {
    val owner = ctx.curOperation?.methodOwner
    val curOperation = ctx.curOperation
    if (ctx.curState == null || curOperation == null || owner == null) {
        throw UnregisteredVirtualOperation()
    }
    val ownerIsAlreadyMocked = ctx.extractCurState().mockedObjects.contains(owner)
    var clonedState = if (!ownerIsAlreadyMocked) ctx.extractCurState().clone() else null
    if (clonedState != null) {
        clonedState = pyAssertOnState(clonedState, mkHeapRefEq(owner.address, nullRef).not())
    }
    val (symbolic, isNew, mockSymbol) = ctx.extractCurState().mock(curOperation)
    if (!ownerIsAlreadyMocked && clonedState != null) {
        addDelayedFork(ctx, owner, clonedState)
    }
    if (curOperation.method.isMethodWithNonVirtualReturn && isNew) {
        val customNewModels = customNewModelsCreation(mockSymbol)
        val (newModel, constraint) =
            if (customNewModels.isEmpty()) {
                val oldModel = ctx.modelHolder.model
                constructModelWithNewMockEvaluator(
                    ctx.ctx,
                    oldModel,
                    mockSymbol,
                    UseOldPathConstraintsInfo(oldModel.psInfo),
                )
            } else {
                customNewModels.first()
            }

        customNewModels.drop(1).forEach { (nextNewModel, constraint) ->
            val newState = ctx.extractCurState().clone()
            newState.models = listOf(nextNewModel)
            newState.pathConstraints += constraint
            ctx.forkedStates.add(newState)
        }

        substituteModel(ctx.extractCurState(), newModel, constraint, ctx)
    }
    val concrete = interpretSymbolicPythonObject(ctx, symbolic)
    return concrete to symbolic
}

fun virtualCallKt(ctx: ConcolicRunContext): PyObject {
    ctx.curState ?: throw UnregisteredVirtualOperation()
    val (interpreted, _) = internalVirtualCallKt(ctx)
    val objectModel = ctx.builder.convert(interpreted)
    return ctx.renderer.convert(objectModel)
}

fun virtualCallSymbolKt(ctx: ConcolicRunContext): UninterpretedSymbolicPythonObject {
    ctx.curState ?: throw UnregisteredVirtualOperation()
    val curOperation = ctx.curOperation ?: throw UnregisteredVirtualOperation()
    val result = internalVirtualCallKt(ctx).second
    if (!curOperation.method.isMethodWithNonVirtualReturn) {
        val softConstraint = ctx.ctx.mkHeapRefEq(result.address, ctx.ctx.nullRef)
        val ps = ctx.extractCurState().pathConstraints
        ps.pythonSoftConstraints = ps.pythonSoftConstraints.add(softConstraint)
    }
    return result
}
