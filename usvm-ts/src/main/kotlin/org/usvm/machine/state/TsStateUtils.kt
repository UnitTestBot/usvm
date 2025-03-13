package org.usvm.machine.state

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.model.TsLocal
import org.usvm.model.TsMethod
import org.usvm.model.TsStmt
import org.usvm.util.getDeclaredLocals
import org.usvm.util.getLocals

val TsState.lastStmt get() = pathNode.statement

fun TsState.newStmt(stmt: TsStmt) {
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

inline val TsMethod.parametersWithThisCount: Int
    get() = parameters.size + 1

inline val TsMethod.allLocals: Set<TsLocal>
    get() = getDeclaredLocals() + getLocals()

inline val TsMethod.localsCount: Int
    get() = allLocals.size
