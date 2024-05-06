package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaExprVisitor
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaValue

sealed class PandaBinaryOperationAuxiliaryExpr(val originalExpr: PandaBinaryExpr) : PandaExpr {
    override val operands: List<PandaValue>
        get() = originalExpr.operands
    override val type: PandaType
        get() = originalExpr.type

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaExpr(this)
    }

    class NumberToNumber(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class NumberToBoolean(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class NumberToString(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class NumberToObject(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    class BooleanToNumber(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class BooleanToBoolean(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class BooleanToString(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class BooleanToObjects(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    class StringToNumber(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class StringToBoolean(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class StringToString(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class StringToObject(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    class ObjectToNumber(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class ObjectToBoolean(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class ObjectToString(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class ObjectToObjects(originalExpr: PandaBinaryExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    companion object {
        fun specializeBinaryOperation(
            expr: PandaBinaryExpr,
            fst: PandaType,
            snd: PandaType,
        ): PandaExpr {
            return when (fst) {
                is PandaNumberType -> when (snd) {
                    is PandaNumberType -> NumberToNumber(expr)
                    is PandaBoolType -> NumberToBoolean(expr)
                    is PandaStringType -> NumberToString(expr)
                    else -> NumberToObject(expr)
                }

                is PandaBoolType -> when (snd) {
                    is PandaNumberType -> BooleanToNumber(expr)
                    is PandaBoolType -> BooleanToBoolean(expr)
                    is PandaStringType -> BooleanToString(expr)
                    else -> BooleanToObjects(expr)
                }

                is PandaStringType -> when (snd) {
                    is PandaNumberType -> StringToNumber(expr)
                    is PandaBoolType -> StringToBoolean(expr)
                    is PandaStringType -> StringToString(expr)
                    else -> StringToObject(expr)
                }

                else -> when (snd) {
                    is PandaNumberType -> ObjectToNumber(expr)
                    is PandaBoolType -> ObjectToBoolean(expr)
                    is PandaStringType -> ObjectToString(expr)
                    else -> ObjectToObjects(expr)
                }
            }
        }
    }
}
