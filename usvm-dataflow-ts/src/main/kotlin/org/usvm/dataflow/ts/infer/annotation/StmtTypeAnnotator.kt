/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact

class StmtTypeAnnotator(
    types: Map<AccessPathBase, EtsTypeFact>,
    thisType: EtsTypeFact?,
    scene: EtsScene,
) : EtsStmt.Visitor<EtsStmt> {
    private val valueAnnotator = ValueTypeAnnotator(types, thisType, scene)
    private val exprAnnotator = ExprTypeAnnotator(valueAnnotator, scene)

    private fun inferValue(value: EtsValue) = value.accept(valueAnnotator)

    private fun inferExpr(expr: EtsExpr) = expr.accept(exprAnnotator)

    private fun inferEntity(entity: EtsEntity) = when (entity) {
        is EtsExpr -> entity.accept(exprAnnotator)
        is EtsValue -> entity.accept(valueAnnotator)
        else -> error("Unsupported entity")
    }

    override fun visit(stmt: EtsNopStmt) = stmt

    override fun visit(stmt: EtsAssignStmt) = stmt.copy(
        lhv = inferValue(stmt.lhv),
        rhv = inferEntity(stmt.rhv)
    )

    override fun visit(stmt: EtsCallStmt) = stmt.copy(
        expr = inferExpr(stmt.expr) as EtsCallExpr
    )

    override fun visit(stmt: EtsReturnStmt) = stmt.copy(
        returnValue = stmt.returnValue?.let(this::inferValue)
    )

    override fun visit(stmt: EtsThrowStmt) = stmt.copy(
        arg = inferEntity(stmt.arg)
    )

    override fun visit(stmt: EtsGotoStmt) = stmt

    override fun visit(stmt: EtsIfStmt) = stmt.copy(
        condition = inferEntity(stmt.condition)
    )

    override fun visit(stmt: EtsSwitchStmt) = stmt.copy(
        arg = inferEntity(stmt.arg),
        cases = stmt.cases.map(this::inferEntity)
    )
}
