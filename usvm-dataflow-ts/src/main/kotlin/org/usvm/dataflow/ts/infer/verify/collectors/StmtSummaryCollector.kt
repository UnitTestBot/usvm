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

import org.jacodb.ets.base.EtsAssignStmt
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
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ts.infer.verify.EntityId

class StmtSummaryCollector(
    override val enclosingMethod: EtsMethodSignature,
    override val typeSummary: MutableMap<EntityId, MutableSet<EtsType>>
) : EtsStmt.Visitor<Unit>, MethodSummaryCollector {
    private val exprCollector = ExprSummaryCollector(enclosingMethod, typeSummary)
    private val valueCollector = ValueSummaryCollector(enclosingMethod, typeSummary)

    private fun collect(entity: EtsEntity) {
        when (entity) {
            is EtsValue -> entity.accept(valueCollector)
            is EtsExpr -> entity.accept(exprCollector)
            else -> error("Unsupported entity kind")
        }
    }

    override fun visit(stmt: EtsNopStmt) {}

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

    override fun visit(stmt: EtsGotoStmt) {}

    override fun visit(stmt: EtsIfStmt) {
        collect(stmt.condition)
    }

    override fun visit(stmt: EtsSwitchStmt) {
        collect(stmt.arg)
        stmt.cases.forEach { collect(it) }
    }

}