package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaExprVisitor
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaValue

sealed class PandaBinaryOperationAuxiliaryExpr(val originalExpr: PandaExpr) : PandaExpr {
    override val operands: List<PandaValue>
        get() = originalExpr.operands
    override val type: PandaType
        get() = originalExpr.type

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        TODO("Not yet implemented")
    }

    class NumberToNumber(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class NumberToBoolean(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class NumberToString(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class NumberToObject(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    class BooleanToNumber(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class BooleanToBoolean(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class BooleanToString(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class BooleanToObjects(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    class StringToNumber(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class StringToBoolean(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class StringToString(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class StringToObject(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    class ObjectToNumber(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class ObjectToBoolean(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class ObjectToString(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)
    class ObjectToObjects(originalExpr: PandaExpr) : PandaBinaryOperationAuxiliaryExpr(originalExpr)

    companion object {
        fun specializeBinaryOperation(
            expr: PandaExpr,
            fst: PandaType,
            snd: PandaType,
        ) : PandaExpr {
            if (expr !is PandaBinaryExpr) {
                return expr
            }

            return when (fst) {
                is PandaNumberType -> when (snd) {
                    is PandaNumberType -> NumberToNumber(expr)
                    is PandaBoolType -> NumberToBoolean(expr)
                    else -> NumberToObject(expr)
//                is PandaStringConstant -> NumberToNumberInst(stmt)
                }
                else -> TODO()
            }
        }
    }
}
