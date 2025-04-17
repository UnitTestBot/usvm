package org.usvm.machine

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThrowStmt
import org.usvm.UBoolExpr
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.statistics.UInterpreterObserver

@Suppress("unused")
interface TsInterpreterObserver : UInterpreterObserver {
    fun onAssignStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsAssignStmt,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    // TODO on entry point

    fun onCallWithUnresolvedArguments(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsCallExpr,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    // TODO onCallWithResolvedArguments

    fun onIfStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsIfStmt,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    fun onIfStatementWithResolvedCondition(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsIfStmt,
        condition: UBoolExpr,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    fun onReturnStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsReturnStmt,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    fun onThrowStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsThrowStmt,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    fun onGotoStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsGotoStmt,
        scope: TsStepScope
    ) {
        // default empty implementation
    }

    fun onSwitchStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsSwitchStmt,
        scope: TsStepScope
    ) {
        // default empty implementation
    }
}
