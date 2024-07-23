package org.usvm

import org.jacodb.ets.base.EtsStmt
import org.usvm.state.TSState

fun TSState.newStmt(stmt: EtsStmt) {
    pathNode += stmt
}
