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
    fun onAssignStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsAssignStmt, scope: TsStepScope) {}
    // TODO on entry point
    fun onCallWithUnresolvedArguments(simpleValueResolver: TsSimpleValueResolver, stmt: EtsCallExpr, scope: TsStepScope) {}
    // TODO onCallWithResolvedArguments
    fun onIfStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsIfStmt, scope: TsStepScope) {}
    fun onIfStatementWithResolvedCondition(simpleValueResolver: TsSimpleValueResolver, stmt: EtsIfStmt, condition: UBoolExpr, scope: TsStepScope) {}
    fun onReturnStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsReturnStmt, scope: TsStepScope) {}
    fun onThrowStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsThrowStmt, scope: TsStepScope) {}
    fun onGotoStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsGotoStmt, scope: TsStepScope) {}
    fun onSwitchStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsSwitchStmt, scope: TsStepScope) {}
}