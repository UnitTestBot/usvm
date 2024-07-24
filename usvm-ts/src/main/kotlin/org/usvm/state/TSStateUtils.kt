package org.usvm.state

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

fun EtsMethod.localIdx(idx: Int) = if (isStatic) idx else idx + 1

inline val EtsMethod.parametersWithThisCount get() = localIdx(parameters.size)