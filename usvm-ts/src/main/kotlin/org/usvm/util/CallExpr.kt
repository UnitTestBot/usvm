package org.usvm.util

import org.usvm.model.TsAssignStmt
import org.usvm.model.TsCallExpr
import org.usvm.model.TsCallStmt
import org.usvm.model.TsEntity
import org.usvm.model.TsStmt

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
