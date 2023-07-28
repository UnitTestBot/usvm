package org.usvm.interpreter.operations

import org.usvm.interpreter.*
import org.usvm.interpreter.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.isTrue
import org.usvm.language.NbBoolMethod
import org.usvm.language.NbIntMethod
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.TypeOfVirtualObject
import org.usvm.language.types.pythonBool
import org.usvm.language.types.pythonInt
import org.usvm.types.first

fun virtualNbBoolKt(context: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == NbBoolMethod && interpretedArg == on.interpretedObj)
    val (virtualObject, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertype(context, pythonBool)
    val boolValue = virtualObject.interpretedObj.getBoolContent(context)
    myFork(context, boolValue)
    return boolValue.isTrue
}

fun virtualNbIntKt(context: ConcolicRunContext, on: VirtualPythonObject): PythonObject {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == NbIntMethod && interpretedArg == on.interpretedObj)
    val (virtualObject, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertype(context, pythonInt)
    val intValue = virtualObject.interpretedObj.getIntContent(context)
    return ConcretePythonInterpreter.eval(emptyNamespace, intValue.toString())
}

private fun internalVirtualCallKt(context: ConcolicRunContext): Pair<VirtualPythonObject, UninterpretedSymbolicPythonObject> = with(context.ctx) {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val interpretedOwner =
        interpretSymbolicPythonObject(
            context.curOperation.methodOwner.obj,
            context.modelHolder
        ) as InterpretedInputSymbolicPythonObject
    val typeStreamOfOwner = interpretedOwner.getTypeStream()
    val ownerIsAlreadyMocked = typeStreamOfOwner.first() == TypeOfVirtualObject && typeStreamOfOwner.take(2).size == 1
    val clonedState = if (!ownerIsAlreadyMocked) context.curState.clone() else null
    val (symbolic, _, mockSymbol) = context.curState.mock(context.curOperation)
    if (!ownerIsAlreadyMocked) {
        addDelayedFork(context, context.curOperation.methodOwner.obj, clonedState!!)
    }
    if (context.curOperation.method.isMethodWithNonVirtualReturn) {
        val newModel = constructModelWithNewMockEvaluator(context.ctx, context.modelHolder.model, mockSymbol)
        substituteModel(context.curState, newModel, context)
    }
    val concrete = interpretSymbolicPythonObject(symbolic, context.modelHolder)
    return VirtualPythonObject(concrete as InterpretedInputSymbolicPythonObject) to symbolic
}

fun virtualCallKt(context: ConcolicRunContext): VirtualPythonObject = internalVirtualCallKt(context).first
fun virtualCallSymbolKt(context: ConcolicRunContext): UninterpretedSymbolicPythonObject = internalVirtualCallKt(context).second

object UnregisteredVirtualOperation: Exception()