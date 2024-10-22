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

import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsExpr
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsPtrCallExpr
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ts.infer.verify.EntityId

class ExprSummaryCollector(
    override val enclosingMethod: EtsMethodSignature,
    override val typeSummary: MutableMap<EntityId, MutableSet<EtsType>>
) : MethodSummaryCollector, EtsExpr.Visitor.Default<Unit> {
    private val valueSummaryCollector by lazy {
        ValueSummaryCollector(enclosingMethod, typeSummary)
    }

    private fun collect(entity: EtsEntity) {
        when (entity) {
            is EtsValue -> entity.accept(valueSummaryCollector)
            is EtsExpr -> entity.accept(this)
            else -> error("Unsupported entity kind")
        }
    }

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
            yield(expr.method)
            collect(expr.instance)
            expr.args.forEach { collect(it) }
        }

        is EtsStaticCallExpr -> {
            yield(expr.method)
            expr.args.forEach { collect(it) }
        }

        is EtsPtrCallExpr -> {
            yield(expr.method)
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
}
