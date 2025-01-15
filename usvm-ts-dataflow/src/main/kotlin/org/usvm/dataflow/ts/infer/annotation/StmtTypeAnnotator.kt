/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsExpr
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsRawStmt
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsValue

class StmtTypeAnnotator(
    private val valueAnnotator: ValueTypeAnnotator,
    private val exprAnnotator: ExprTypeAnnotator,
) : EtsStmt.Visitor<EtsStmt> {

    private fun annotate(value: EtsValue) = value.accept(valueAnnotator)

    private fun annotate(expr: EtsExpr) = expr.accept(exprAnnotator)

    private fun annotate(entity: EtsEntity) = when (entity) {
        is EtsValue -> annotate(entity)
        is EtsExpr -> annotate(entity)
        else -> error("Unsupported entity of type ${entity::class.java}: $entity")
    }

    override fun visit(stmt: EtsNopStmt) = stmt

    override fun visit(stmt: EtsAssignStmt) = stmt.copy(
        lhv = annotate(stmt.lhv),
        rhv = annotate(stmt.rhv),
    )

    override fun visit(stmt: EtsCallStmt) = stmt.copy(
        expr = annotate(stmt.expr) as EtsCallExpr
    )

    override fun visit(stmt: EtsReturnStmt) = stmt.copy(
        returnValue = stmt.returnValue?.let { annotate(it) }
    )

    override fun visit(stmt: EtsThrowStmt) = stmt.copy(
        arg = annotate(stmt.arg)
    )

    override fun visit(stmt: EtsGotoStmt) = stmt

    override fun visit(stmt: EtsIfStmt) = stmt.copy(
        condition = annotate(stmt.condition)
    )

    override fun visit(stmt: EtsSwitchStmt) = stmt.copy(
        arg = annotate(stmt.arg),
        cases = stmt.cases.map { annotate(it) },
    )

    override fun visit(stmt: EtsRawStmt): EtsStmt = stmt
}
