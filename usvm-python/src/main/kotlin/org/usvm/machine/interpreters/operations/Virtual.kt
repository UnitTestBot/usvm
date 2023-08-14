package org.usvm.machine.interpreters.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.*
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.isTrue
import org.usvm.language.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.machine.utils.substituteModel

fun virtualNbBoolKt(context: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val typeSystem = context.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == NbBoolMethod && interpretedArg == on.interpretedObj)
    val (interpretedObj, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertype(context, typeSystem.pythonBool)
    myFork(context, symbolic.getBoolContent(context))
    return interpretedObj.getBoolContent(context).isTrue
}

fun virtualNbIntKt(context: ConcolicRunContext, on: VirtualPythonObject): PythonObject {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val typeSystem = context.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == NbIntMethod && interpretedArg == on.interpretedObj)
    val (interpretedObj, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertype(context, typeSystem.pythonInt)
    val intValue = interpretedObj.getIntContent(context)
    return ConcretePythonInterpreter.eval(emptyNamespace, intValue.toString())
}

fun virtualSqLengthKt(context: ConcolicRunContext, on: VirtualPythonObject): Int = with(context.ctx) {
    context.curOperation ?: throw UnregisteredVirtualOperation
    val typeSystem = context.typeSystem
    val interpretedArg = interpretSymbolicPythonObject(context.curOperation!!.args.first().obj, context.modelHolder)
    require(context.curOperation?.method == SqLengthMethod && interpretedArg == on.interpretedObj)
    val (interpretedObj, symbolic) = internalVirtualCallKt(context)
    symbolic.addSupertype(context, typeSystem.pythonInt)
    val intValue = interpretedObj.getIntContent(context)
    myAssert(context, intValue le mkIntNum(Int.MAX_VALUE))
    return intValue.toString().toInt()
}

private fun internalVirtualCallKt(context: ConcolicRunContext): Pair<InterpretedInputSymbolicPythonObject, UninterpretedSymbolicPythonObject> = with(context.ctx) {
    context.curOperation ?: throw UnregisteredVirtualOperation
    context.curState ?: throw UnregisteredVirtualOperation
    val owner = context.curOperation.methodOwner ?: throw UnregisteredVirtualOperation
    val ownerIsAlreadyMocked = context.curState!!.mockedObjects.contains(owner)
    var clonedState = if (!ownerIsAlreadyMocked) context.curState!!.clone() else null
    if (clonedState != null) {
        clonedState = myAssertOnState(clonedState, mkHeapRefEq(owner.obj.address, nullRef).not())
    }
    val (symbolic, _, mockSymbol) = context.curState!!.mock(context.curOperation)
    if (!ownerIsAlreadyMocked && clonedState != null) {
        addDelayedFork(context, owner.obj, clonedState)
    }
    if (context.curOperation.method.isMethodWithNonVirtualReturn) {
        val newModel = constructModelWithNewMockEvaluator(context.ctx, context.modelHolder.model, mockSymbol)
        substituteModel(context.curState!!, newModel, context)
    }
    val concrete = interpretSymbolicPythonObject(symbolic, context.modelHolder)
    return (concrete as InterpretedInputSymbolicPythonObject) to symbolic
}

fun virtualCallKt(context: ConcolicRunContext): PythonObject {
    val (interpreted, _) = internalVirtualCallKt(context)
    val converter = context.converter
    return converter.convert(interpreted)
}

fun virtualCallSymbolKt(context: ConcolicRunContext): UninterpretedSymbolicPythonObject = internalVirtualCallKt(context).second

object UnregisteredVirtualOperation: Exception()