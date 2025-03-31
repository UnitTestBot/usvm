package org.usvm.util

import org.usvm.machine.expr.*

val TsStmt.callExpr: TsCallExpr?
    get() = when (this) {
        is TsCallStmt -> expr
        is TsAssignStmt -> rhv.callExpr
        else -> null
    }

val TsEntity.callExpr: TsCallExpr?
    get() = when (this) {
        is TsCallExpr -> this
        else -> null
    }
