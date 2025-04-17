package org.usvm.machine.state

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UExpr
import org.usvm.USort

val TsState.lastStmt get() = pathNode.statement

fun TsState.newStmt(stmt: EtsStmt) {
    pathNode += stmt
}

fun TsState.returnValue(valueToReturn: UExpr<out USort>) {
    val returnFromMethod = callStack.lastMethod()
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
        popLocalToSortStack()
    }

    methodResult = TsMethodResult.Success.RegularCall(returnFromMethod, valueToReturn)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

inline val EtsMethod.parametersWithThisCount: Int
    get() = parameters.size + 1

inline val EtsMethod.localsCount: Int
    get() = getDeclaredLocals().size
