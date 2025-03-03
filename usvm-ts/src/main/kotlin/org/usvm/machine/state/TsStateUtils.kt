package org.usvm.machine.state

import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.getDeclaredLocals
import org.jacodb.ets.utils.getLocals
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

    methodResult = TsMethodResult.Success(returnFromMethod, valueToReturn)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

inline val EtsMethod.parametersWithThisCount: Int
    get() = parameters.size + 1

inline val EtsMethod.allLocals: Set<EtsLocal>
    get() = getDeclaredLocals() + getLocals()

inline val EtsMethod.localsCount: Int
    get() = allLocals.size
