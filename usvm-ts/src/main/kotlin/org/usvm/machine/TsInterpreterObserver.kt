package org.usvm.machine

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThrowStmt
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.statistics.UInterpreterObserver

@Suppress("unused")
class TsInterpreterObserver : UInterpreterObserver {
    fun onAssignStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsAssignStmt, stepScope: TsStepScope) {}
    // TODO on entry point
    fun onCallWithUnresolvedArguments(simpleValueResolver: TsSimpleValueResolver, stmt: EtsCallExpr, stepScope: TsStepScope) {}
    // TODO onCallWithResolvedArguments
    fun onIfStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsIfStmt, stepScope: TsStepScope) {}
    fun onReturnStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsReturnStmt, stepScope: TsStepScope) {}
    fun onThrowStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsThrowStmt, stepScope: TsStepScope) {}
    fun onGotoStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsGotoStmt, stepScope: TsStepScope) {}
    fun onSwitchStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsSwitchStmt, stepScope: TsStepScope) {}
}