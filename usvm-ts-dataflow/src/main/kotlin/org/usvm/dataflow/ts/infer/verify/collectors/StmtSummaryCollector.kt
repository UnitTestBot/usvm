/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ts.infer.verify.collectors

import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsExpr
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsPtrCallExpr
import org.jacodb.ets.base.EtsRawStmt
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethodSignature

class StmtSummaryCollector(
    override val method: EtsMethodSignature,
    override val verificationSummary: MethodVerificationSummary,
) : SummaryCollector,
    EtsStmt.Visitor<Unit>,
    EtsValue.Visitor.Default<Unit>,
    EtsExpr.Visitor.Default<Unit> {

    private fun collect(value: EtsValue) {
        value.accept(this)
    }

    private fun collect(expr: EtsExpr) {
        expr.accept(this)
    }

    private fun collect(entity: EtsEntity) {
        when (entity) {
            is EtsValue -> collect(entity)
            is EtsExpr -> collect(entity)
            else -> error("Unsupported entity of type ${entity::class.java}: $entity")
        }
    }

    // region Stmt

    override fun visit(stmt: EtsNopStmt) {
        // do nothing
    }

    override fun visit(stmt: EtsAssignStmt) {
        collect(stmt.lhv)
        collect(stmt.rhv)
    }

    override fun visit(stmt: EtsCallStmt) {
        collect(stmt.expr)
    }

    override fun visit(stmt: EtsReturnStmt) {
        stmt.returnValue?.let { collect(it) }
    }

    override fun visit(stmt: EtsThrowStmt) {
        collect(stmt.arg)
    }

    override fun visit(stmt: EtsGotoStmt) {
        // do nothing
    }

    override fun visit(stmt: EtsIfStmt) {
        collect(stmt.condition)
    }

    override fun visit(stmt: EtsSwitchStmt) {
        collect(stmt.arg)
        stmt.cases.forEach { collect(it) }
    }

    override fun visit(stmt: EtsRawStmt) {
        // do nothing
    }

    // endregion

    // region Value

    override fun defaultVisit(value: EtsValue) {}

    override fun visit(value: EtsLocal) {
        yield(value)
    }

    override fun visit(value: EtsThis) {
        yield(value)
    }

    override fun visit(value: EtsParameterRef) {
        yield(value)
    }

    override fun visit(value: EtsArrayAccess) {
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visit(value: EtsInstanceFieldRef) {
        requireObjectOrUnknown(value.instance, value)
        value.instance.accept(this)
    }

    override fun visit(value: EtsStaticFieldRef) {
    }

    // endregion

    // region Expr

    override fun defaultVisit(expr: EtsExpr) = when (expr) {
        is EtsUnaryExpr -> {
            collect(expr.arg)
        }

        is EtsBinaryExpr -> {
            collect(expr.left)
            collect(expr.right)
        }

        is EtsTernaryExpr -> {
            collect(expr.condition)
            collect(expr.thenExpr)
            collect(expr.elseExpr)
        }

        is EtsInstanceCallExpr -> {
            collect(expr.instance)
            requireObjectOrUnknown(expr.instance, expr)
            expr.args.forEach { collect(it) }
        }

        is EtsStaticCallExpr -> {
            expr.args.forEach { collect(it) }
        }

        is EtsPtrCallExpr -> {
            collect(expr.ptr)
            expr.args.forEach { collect(it) }
        }

        is EtsLengthExpr -> {
            collect(expr.arg)
        }

        is EtsInstanceOfExpr -> {
            collect(expr.arg)
        }

        else -> {}
    }

    // endregion
}
