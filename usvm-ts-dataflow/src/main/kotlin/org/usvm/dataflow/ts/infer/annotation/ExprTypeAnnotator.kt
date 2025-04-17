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

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidExpr
import org.jacodb.ets.model.EtsYieldExpr

class ExprTypeAnnotator(
    private val scene: EtsScene,
    private val valueAnnotator: ValueTypeAnnotator,
) : EtsExpr.Visitor<EtsExpr> {

    private fun annotate(value: EtsValue) = value.accept(valueAnnotator)

    private fun annotate(expr: EtsExpr) = expr.accept(this)

    private fun annotate(entity: EtsEntity) = when (entity) {
        is EtsValue -> annotate(entity)
        is EtsExpr -> annotate(entity)
        else -> error("Unsupported entity of type ${entity::class.java}: $entity")
    }

    override fun visit(expr: EtsNewExpr) = expr

    override fun visit(expr: EtsNewArrayExpr) = expr

    override fun visit(expr: EtsCastExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsInstanceOfExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsDeleteExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsAwaitExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsYieldExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsTypeOfExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsVoidExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsNotExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsBitNotExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsNegExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsUnaryPlusExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsPreIncExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsPreDecExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsPostIncExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsPostDecExpr) = expr.copy(
        arg = annotate(expr.arg)
    )

    override fun visit(expr: EtsEqExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsNotEqExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsStrictEqExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsStrictNotEqExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsLtExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsLtEqExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsGtExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsGtEqExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsInExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsAddExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsSubExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsMulExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsDivExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsRemExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsExpExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsBitAndExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsBitOrExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right)
    )

    override fun visit(expr: EtsBitXorExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsLeftShiftExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsRightShiftExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsUnsignedRightShiftExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsAndExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsOrExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsNullishCoalescingExpr) = expr.copy(
        left = annotate(expr.left),
        right = annotate(expr.right),
    )

    override fun visit(expr: EtsInstanceCallExpr): EtsExpr {
        val baseInferred = annotate(expr.instance) as EtsLocal // safe cast
        val argsInferred = expr.args.map { annotate(it) as EtsLocal } // safe cast
        val methodInferred = when (val baseType = baseInferred.type) {
            is EtsClassType -> {
                val etsClass = scene.projectAndSdkClasses.find { it.signature == baseType.signature }
                    ?: return expr.copy(instance = baseInferred, args = argsInferred)
                val callee = etsClass.methods.find { it.signature == expr.callee }
                    ?: return expr.copy(instance = baseInferred, args = argsInferred)
                callee.signature
            }

            else -> expr.callee
        }
        return expr.copy(instance = baseInferred, callee = methodInferred, args = argsInferred)
    }

    override fun visit(expr: EtsStaticCallExpr): EtsExpr {
        val argsInferred = expr.args.map { annotate(it) as EtsLocal } // safe cast
        return expr.copy(args = argsInferred)
    }

    override fun visit(expr: EtsPtrCallExpr): EtsExpr {
        val ptrInferred = annotate(expr.ptr) as EtsLocal // safe cast
        val argsInferred = expr.args.map { annotate(it) as EtsLocal } // safe cast
        return expr.copy(ptr = ptrInferred, args = argsInferred)
    }
}
