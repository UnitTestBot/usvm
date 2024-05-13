package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaMethod
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.state.PandaState
import org.usvm.machine.state.lastStmt

fun PandaState.addVirtualMethodCallStmt(method: PandaMethod, arguments: List<UExpr<out USort>>) {
    newStmt(PandaVirtualMethodCallInst(lastStmt.location, method, arguments, lastStmt))
}