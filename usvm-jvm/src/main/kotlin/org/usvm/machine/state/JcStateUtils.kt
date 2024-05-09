package org.usvm.machine.state

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.locals
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcDynamicMethodCallInst
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcVirtualMethodCallInst

val JcState.lastStmt get() = pathLocation.statement
fun JcState.newStmt(stmt: JcInst) {
    pathLocation = pathLocation.pathLocationFor(stmt, this)
}

fun JcState.returnValue(valueToReturn: UExpr<out USort>) {
    val returnFromMethod = callStack.lastMethod()
    // TODO: think about it later
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    methodResult = JcMethodResult.Success(returnFromMethod, valueToReturn)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

/**
 * Create an unprocessed exception with the [address] and the [type] and assign it to the [JcState.methodResult].
 */
fun JcState.throwExceptionWithoutStackFrameDrop(address: UHeapRef, type: JcType) {
    methodResult = JcMethodResult.JcException(address, type, callStack.stackTrace(lastStmt))
}

fun JcState.throwExceptionAndDropStackFrame() {
    // Exception is allowed to be thrown only after
    // it is created via `throwExceptionWithoutStackFrameDrop` function
    require(methodResult is JcMethodResult.JcException)

    // TODO: think about it later
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

fun JcState.addNewMethodCall(
    methodCall: JcConcreteMethodCallInst
) {
    val method = methodCall.method
    val entryPoint = methodCall.entrypoint
        ?: error("No entrypoint found for method: $method")
    callStack.push(method, methodCall.returnSite)
    memory.stack.push(methodCall.arguments.toTypedArray(), method.localsCount)
    newStmt(entryPoint)
}

fun JcState.addConcreteMethodCallStmt(
    method: JcMethod,
    arguments: List<UExpr<out USort>>,
    applicationGraph: JcApplicationGraph
) {
    val unresolvedCall = JcConcreteMethodCallInst(method, lastStmt.location, arguments, lastStmt)
    newStmt(unresolvedCall.resolveEntrypoint(applicationGraph))
}

fun JcState.addVirtualMethodCallStmt(method: JcMethod, arguments: List<UExpr<out USort>>) {
    newStmt(JcVirtualMethodCallInst(lastStmt.location, method, arguments, lastStmt))
}

fun JcState.addDynamicCall(dynamicCall: JcDynamicCallExpr, arguments: List<UExpr<out USort>>) {
    newStmt(JcDynamicMethodCallInst(dynamicCall, arguments, lastStmt))
}

fun JcMethod.localIdx(idx: Int) = if (isStatic) idx else idx + 1

// TODO: cache it with JacoDB cache
inline val JcMethod.parametersWithThisCount get() = localIdx(parameters.size)

// TODO: cache it with JacoDB cache
inline val JcMethod.localsCount get() = instList.locals.filter { it !is JcArgument }.size

fun JcState.skipMethodInvocationWithValue(methodCall: JcMethodCall, value: UExpr<out USort>) {
    methodResult = JcMethodResult.Success(methodCall.method, value)
    newStmt(methodCall.returnSite)
}
