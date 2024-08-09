package org.usvm.state

import org.jacodb.ets.base.EtsStmt
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
