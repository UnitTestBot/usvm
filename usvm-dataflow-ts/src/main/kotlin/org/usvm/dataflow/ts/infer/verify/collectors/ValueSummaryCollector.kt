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
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsObjectLiteral
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ts.infer.verify.EntityId

class ValueSummaryCollector(
    override val enclosingMethod: EtsMethodSignature,
    override val typeSummary: MutableMap<EntityId, MutableSet<EtsType>>,
) : MethodSummaryCollector, EtsValue.Visitor.Default<Unit> {

    private val exprSummaryCollector by lazy {
        ExprSummaryCollector(enclosingMethod, typeSummary)
    }

    private fun collect(entity: EtsEntity) {
        when (entity) {
            is EtsValue -> entity.accept(this@ValueSummaryCollector)
            is EtsExpr -> entity.accept(exprSummaryCollector)
            else -> error("Unsupported entity of type ${entity::class.java}: $entity")
        }
    }

    override fun defaultVisit(value: EtsValue) {}

    override fun visit(value: EtsLocal) {
        yield(value)
    }

    override fun visit(value: EtsArrayLiteral) {
        value.elements.forEach { collect(it) }
    }

    override fun visit(value: EtsObjectLiteral) {
        value.properties.forEach { (_, it) -> collect(it) }
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
        yield(value.field)
        value.instance.accept(this)
    }

    override fun visit(value: EtsStaticFieldRef) {
        yield(value.field)
    }
}
