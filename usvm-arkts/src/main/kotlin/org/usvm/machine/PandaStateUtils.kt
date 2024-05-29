package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.state.PandaState
import org.usvm.machine.state.lastStmt

fun PandaState.addVirtualMethodCallStmt(method: PandaMethod, arguments: List<UExpr<out USort>>) {
    newStmt(PandaVirtualMethodCallInst(lastStmt.location, method, arguments, lastStmt))
}

fun PandaState.addNewMethodCall(methodCall: PandaConcreteMethodCallInst, entryPoint: PandaInst) {
    val method = methodCall.method
    callStack.push(method, methodCall.returnSite)
    memory.stack.push(methodCall.arguments.toTypedArray(), method.localVarsCount)
    newStmt(entryPoint)
}