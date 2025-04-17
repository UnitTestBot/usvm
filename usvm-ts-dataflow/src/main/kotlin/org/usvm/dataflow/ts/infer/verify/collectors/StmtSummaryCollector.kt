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

import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsUnaryExpr
import org.jacodb.ets.model.EtsValue

class StmtSummaryCollector(
    override val method: EtsMethod,
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
        collect(stmt.exception)
    }

    override fun visit(stmt: EtsIfStmt) {
        collect(stmt.condition)
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

        is EtsInstanceOfExpr -> {
            collect(expr.arg)
        }

        else -> {}
    }

    // endregion
}
