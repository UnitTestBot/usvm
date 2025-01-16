package org.usvm.machine.state

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.machine.expr.MultiExpr

val TSState.lastStmt get() = pathNode.statement

fun TSState.newStmt(stmt: EtsStmt) {
    pathNode += stmt
}

fun TSState.returnValue(valueToReturn: MultiExpr) {
    val returnFromMethod = callStack.lastMethod()
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    methodResult = TSMethodResult.Success(returnFromMethod, valueToReturn)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

inline val EtsMethod.parametersWithThisCount: Int
    get() = if (isStatic) parameters.size else parameters.size + 1

inline val EtsMethod.localsCount: Int
    get() = locals.size
