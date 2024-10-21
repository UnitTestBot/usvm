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

import org.jacodb.ets.base.*
import org.jacodb.ets.model.EtsScene

class ExprTypeAnnotator(
    private val valueAnnotator: ValueTypeAnnotator,
    private val scene: EtsScene
) : EtsExpr.Visitor<EtsExpr> {
    private fun inferEntity(entity: EtsEntity) = when (entity) {
        is EtsExpr -> entity.accept(this)
        is EtsValue -> entity.accept(valueAnnotator)
        else -> error("Unsupported entity")
    }
    private fun inferValue(value: EtsValue) = value.accept(valueAnnotator)

    override fun visit(expr: EtsNewExpr) = expr

    override fun visit(expr: EtsNewArrayExpr) = expr

    override fun visit(expr: EtsLengthExpr) = expr

    override fun visit(expr: EtsCastExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsInstanceOfExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsDeleteExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsAwaitExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsYieldExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsTypeOfExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsVoidExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsNotExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsBitNotExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsNegExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsUnaryPlusExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsPreIncExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsPreDecExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsPostIncExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsPostDecExpr) = expr.copy(arg = inferEntity(expr.arg))

    override fun visit(expr: EtsEqExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsNotEqExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsStrictEqExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsStrictNotEqExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsLtExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsLtEqExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsGtExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsGtEqExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsInExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsAddExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsSubExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsMulExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsDivExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsRemExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsExpExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsBitAndExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsBitOrExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsBitXorExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsLeftShiftExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsRightShiftExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsUnsignedRightShiftExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsAndExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsOrExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsNullishCoalescingExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsInstanceCallExpr): EtsExpr {
        val baseInferred = inferValue(expr.instance) as EtsLocal
        val argsInferred = expr.args.map(this::inferValue)
        val methodInferred = when (val baseType = baseInferred.type) {
            is EtsClassType -> {
                val etsClass = scene.classes.find { it.signature == baseType.classSignature }
                    ?: return expr.copy(instance = baseInferred, args = argsInferred)
                val callee = etsClass.methods.find { it.signature == expr.method }
                    ?: return expr.copy(instance = baseInferred, args = argsInferred)
                callee.signature
            }
            else -> expr.method
        }
        return EtsInstanceCallExpr(baseInferred, methodInferred, argsInferred)
    }

    override fun visit(expr: EtsStaticCallExpr): EtsExpr {
        val argsInferred = expr.args.map(this::inferValue)
        return EtsStaticCallExpr(expr.method, argsInferred)
    }

    override fun visit(expr: EtsPtrCallExpr): EtsExpr {
        val ptrInferred = inferValue(expr.ptr) as EtsLocal
        val argsInferred = expr.args.map(this::inferValue)
        return EtsPtrCallExpr(ptrInferred, expr.method, argsInferred)
    }

    override fun visit(expr: EtsCommaExpr) = expr.copy(
        left = inferEntity(expr.left),
        right = inferEntity(expr.right)
    )

    override fun visit(expr: EtsTernaryExpr) = expr.copy(
        condition = inferEntity(expr.condition),
        thenExpr = inferEntity(expr.thenExpr),
        elseExpr = inferEntity(expr.elseExpr)
    )
}
