package org.usvm.machine

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.usvm.UBoolExpr
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.statistics.UInterpreterObserver

@Suppress("unused")
interface TsInterpreterObserver : UInterpreterObserver {
    fun onAssignStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsAssignStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    // TODO on entry point

    fun onCallWithUnresolvedArguments(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsCallExpr,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    // TODO onCallWithResolvedArguments

    fun onIfStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsIfStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    fun onIfStatementWithResolvedCondition(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsIfStmt,
        condition: UBoolExpr,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    fun onReturnStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsReturnStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    fun onThrowStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsThrowStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }
}
