package org.usvm.machine.state

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.UExpr
import org.usvm.USort

val TSState.lastStmt get() = pathNode.statement

fun TSState.newStmt(stmt: EtsStmt) {
    pathNode += stmt
}

fun TSState.returnValue(valueToReturn: UExpr<out USort>) {
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
    get() = parameters.size + 1

inline val EtsMethod.localsCount: Int
    get() = locals.size
