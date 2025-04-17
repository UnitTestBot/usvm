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

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsLValue
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsValue

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
        lhv = annotate(stmt.lhv) as EtsLValue, // safe cast
        rhv = annotate(stmt.rhv),
    )

    override fun visit(stmt: EtsCallStmt) = stmt.copy(
        expr = annotate(stmt.expr) as EtsCallExpr
    )

    override fun visit(stmt: EtsReturnStmt) = stmt.copy(
        returnValue = stmt.returnValue?.let { annotate(it) as EtsLocal } // safe cast
    )

    override fun visit(stmt: EtsThrowStmt) = stmt.copy(
        exception = annotate(stmt.exception) as EtsLocal // safe cast
    )

    override fun visit(stmt: EtsIfStmt) = stmt.copy(
        condition = annotate(stmt.condition) as EtsLocal // safe cast
    )

    override fun visit(stmt: EtsRawStmt): EtsStmt = stmt
}
