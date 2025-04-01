package org.usvm.util

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsStmt

val EtsStmt.callExpr: EtsCallExpr?
    get() = when (this) {
        is EtsCallStmt -> expr
        is EtsAssignStmt -> rhv.callExpr
        else -> null
    }

val EtsEntity.callExpr: EtsCallExpr?
    get() = when (this) {
        is EtsCallExpr -> this
        else -> null
    }
