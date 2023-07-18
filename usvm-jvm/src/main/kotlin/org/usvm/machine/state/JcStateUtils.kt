package org.usvm.machine.state

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.locals
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.machine.JcApplicationGraph

val JcState.lastStmt get() = path.last()
fun JcState.newStmt(stmt: JcInst) {
    path = path.add(stmt)
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
 * TODO change docs
 * Create an unprocessed exception from the [exception] and assign it to the [JcState.methodResult].
 */
fun JcState.createUnprocessedException(address: UHeapRef, type: JcType) {
    methodResult = JcMethodResult.UnprocessedException(address, type)
}

fun JcState.throwException(exception: JcMethodResult.Exception) {
    // TODO: think about it later
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    // TODO: the last place where we distinguish implicitly thrown and explicitly thrown exceptions
    methodResult = if (exception is JcMethodResult.UnprocessedException) {
        JcMethodResult.Exception(exception.address, exception.type)
    } else {
        exception
    }

    if (returnSite != null) {
        newStmt(returnSite)
    }
}


fun JcState.addEntryMethodCall(
    applicationGraph: JcApplicationGraph,
    method: JcMethod,
) {
    val entryPoint = applicationGraph.entryPoints(method).single()
    callStack.push(method, returnSite = null)
    memory.stack.push(method.parametersWithThisCount, method.localsCount)
    newStmt(entryPoint)
}

fun JcState.addNewMethodCall(
    applicationGraph: JcApplicationGraph,
    method: JcMethod,
    arguments: List<UExpr<out USort>>,
) {
    // TODO: move to appropriate place
    if (method.enclosingClass.name == "java.lang.Throwable") { // TODO: skipping construction of throwables
        val nextStmt = applicationGraph.successors(lastStmt).single()
        newStmt(nextStmt)
        return
    }

    // TODO: move to appropriate place. Skip native method in static initializer
    if (method.name == "registerNatives" && method.enclosingClass.name == "java.lang.Class") {
        val nextStmt = applicationGraph.successors(lastStmt).single()
        newStmt(nextStmt)
        return
    }

    // TODO: find concrete implementation (I guess, the method should be already concrete)
    val entryPoint = applicationGraph.entryPoints(method).singleOrNull()
        ?: error("No entrypoint found for method: $method")
    val returnSite = lastStmt
    callStack.push(method, returnSite)
    memory.stack.push(arguments.toTypedArray(), method.localsCount)
    newStmt(entryPoint)
}

fun JcMethod.localIdx(idx: Int) = if (isStatic) idx else idx + 1

// TODO: cache it with JacoDB cache
inline val JcMethod.parametersWithThisCount get() = localIdx(parameters.size)

// TODO: cache it with JacoDB cache
inline val JcMethod.localsCount get() = instList.locals.filter { it !is JcArgument }.size
