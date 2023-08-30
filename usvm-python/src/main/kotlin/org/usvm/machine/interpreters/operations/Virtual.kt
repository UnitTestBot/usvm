package org.usvm.machine.interpreters.operations

import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.*
import org.usvm.language.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.utils.PyModel
import org.usvm.machine.utils.substituteModel

fun virtualNbBoolKt(context: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == NbBoolMethod && interpretedArg == on.interpretedObj)

    val oldModel = context.modelHolder.model
    val (interpretedObj, _) = internalVirtualCallKt(context) { mockSymbol ->
        val trueObject = context.modelHolder.model.eval(context.curState!!.preAllocatedObjects.trueObject.address)
        val falseObject = context.modelHolder.model.eval(context.curState!!.preAllocatedObjects.falseObject.address)
        listOf(
            constructModelWithNewMockEvaluator(
                context.ctx,
                oldModel,
                mockSymbol,
                falseObject as UConcreteHeapRef
            ),
            constructModelWithNewMockEvaluator(
                context.ctx,
                oldModel,
                mockSymbol,
                trueObject as UConcreteHeapRef
            )
        )
    }

    return interpretedObj.getBoolContent(context).isTrue
}

fun virtualNbIntKt(context: ConcolicRunContext, on: VirtualPythonObject): PythonObject {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val typeSystem = context.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == NbIntMethod && interpretedArg == on.interpretedObj)
    val (interpretedObj, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertypeSoft(context, typeSystem.pythonInt)
    val intValue = interpretedObj.getIntContent(context)
    return ConcretePythonInterpreter.eval(emptyNamespace, intValue.toString())
}

fun virtualSqLengthKt(context: ConcolicRunContext, on: VirtualPythonObject): Int = with(context.ctx) {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val typeSystem = context.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == SqLengthMethod && interpretedArg == on.interpretedObj)
    val (interpretedObj, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertypeSoft(context, typeSystem.pythonInt)
    val intValue = interpretedObj.getIntContent(context)
    myAssert(context, intValue ge mkIntNum(0))
    myAssert(context, intValue le mkIntNum(Int.MAX_VALUE))
    return intValue.toString().toInt()
}

private fun internalVirtualCallKt(
    context: ConcolicRunContext,
    customNewModelsCreation: (UMockSymbol<UAddressSort>) -> List<Pair<PyModel, UBoolExpr>> = { emptyList() }
): Pair<InterpretedSymbolicPythonObject, UninterpretedSymbolicPythonObject> = with(context.ctx) {
    context.curOperation ?: throw UnregisteredVirtualOperation
    context.curState ?: throw UnregisteredVirtualOperation
    val owner = context.curOperation.methodOwner ?: throw UnregisteredVirtualOperation
    val ownerIsAlreadyMocked = context.curState!!.mockedObjects.contains(owner)
    var clonedState = if (!ownerIsAlreadyMocked) context.curState!!.clone() else null
    if (clonedState != null) {
        clonedState = myAssertOnState(clonedState, mkHeapRefEq(owner.obj.address, nullRef).not())
    }
    val (symbolic, isNew, mockSymbol) = context.curState!!.mock(context.curOperation)
    if (!ownerIsAlreadyMocked && clonedState != null) {
        addDelayedFork(context, owner.obj, clonedState)
    }
    if (context.curOperation.method.isMethodWithNonVirtualReturn && isNew) {
        val customNewModels = customNewModelsCreation(mockSymbol)
        val (newModel, constraint) =
            if (customNewModels.isEmpty())
                constructModelWithNewMockEvaluator(context.ctx, context.modelHolder.model, mockSymbol)
            else
                customNewModels.first()

        customNewModels.drop(1).forEach { (nextNewModel, constraint) ->
            val newState = context.curState!!.clone()
            newState.models = listOf(nextNewModel.uModel)
            newState.pathConstraints += constraint
            context.forkedStates.add(newState)
        }

        substituteModel(context.curState!!, newModel, constraint, context)
    }
    val concrete = interpretSymbolicPythonObject(symbolic, context.modelHolder)
    return concrete to symbolic
}

fun virtualCallKt(context: ConcolicRunContext): PythonObject {
    val (interpreted, _) = internalVirtualCallKt(context)
    val converter = context.converter
    require(interpreted is InterpretedInputSymbolicPythonObject)
    return converter.convert(interpreted)
}

fun virtualCallSymbolKt(context: ConcolicRunContext): UninterpretedSymbolicPythonObject = internalVirtualCallKt(context).second

object UnregisteredVirtualOperation: Exception()