package org.usvm.machine

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.cast
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaField
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaObjectType
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.types.UTypeStream
import org.usvm.types.single

sealed class PandaBinaryOperator(
    // TODO undefinedObject?
    val onBool: PandaContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onNumber: PandaContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onString: PandaContext.(UExpr<USort>, UExpr<USort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
) {
    object Add : PandaBinaryOperator(
        onBool = { lhs, rhs ->
            with(lhs.ctx) {
                mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.toNumber(), rhs.toNumber())
            }
        },
        onNumber = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : PandaBinaryOperator(
        onNumber = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : PandaBinaryOperator(
        onNumber = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : PandaBinaryOperator(
        onNumber = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Gt : PandaBinaryOperator(
        onNumber = PandaContext::mkFpGreaterExpr
    )

    object Eq : PandaBinaryOperator(
        onBool = PandaContext::mkEq,
        onNumber = PandaContext::mkFpEqualExpr,
    )

    object Neq : PandaBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onNumber = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    internal open operator fun invoke(
        lhs: PandaUExprWrapper,
        rhs: PandaUExprWrapper,
        typeExtractor: (UConcreteHeapRef) -> UTypeStream<PandaType>,
        fieldReader: (UFieldLValue<PandaField, USort>) -> UExpr<out USort>,
        scope: PandaStepScope
    ): UExpr<out USort> {
        var lhsUExpr = lhs.uExpr
        var rhsUExpr = rhs.uExpr

        val ctx = lhsUExpr.pctx

        if (lhsUExpr is UConcreteHeapRef) {
            val type = typeExtractor(lhsUExpr).single()
            lhsUExpr = when (type) {
                PandaNumberType -> fieldReader(ctx.constructAuxiliaryFieldLValue(lhsUExpr, ctx.fp64Sort))
                PandaBoolType -> fieldReader(ctx.constructAuxiliaryFieldLValue(lhsUExpr, ctx.boolSort))
                PandaStringType -> TODO()
                else -> lhsUExpr
            }
        }

        if (rhsUExpr is UConcreteHeapRef) {
            val type = typeExtractor(rhsUExpr).single()
            rhsUExpr = when (type) {
                PandaNumberType -> fieldReader(ctx.constructAuxiliaryFieldLValue(rhsUExpr, ctx.fp64Sort))
                PandaBoolType -> fieldReader(ctx.constructAuxiliaryFieldLValue(rhsUExpr, ctx.boolSort))
                PandaStringType -> TODO()
                else -> rhsUExpr
            }
        }

        val types = listOf(PandaNumberType, PandaStringType, PandaBoolType, PandaObjectType)

        if (lhsUExpr is KInterpretedValue && rhsUExpr is KInterpretedValue) {
            TODO()
        }

        if (lhsUExpr is KInterpretedValue) {
            TODO()
        }

        if (rhsUExpr is KInterpretedValue) {
            TODO()
        }

        require(lhsUExpr.sort === ctx.addressSort && rhsUExpr.sort === ctx.addressSort)

        if (lhsUExpr.sort != rhsUExpr.sort) {
            rhsUExpr = rhs.withSort(ctx, lhsUExpr.sort).uExpr
        }

        val lhsSort = lhsUExpr.sort
        val rhsSort = rhsUExpr.sort

        return when {

            lhsSort is UBoolSort -> ctx.onBool(lhsUExpr.cast(), rhsUExpr.cast())

            lhsSort is UFpSort -> ctx.onNumber(lhsUExpr.cast(), rhsUExpr.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }
    }

    private fun makeAdditionalWork(
        lhs: UExpr<USort>,
        rhs: UExpr<USort>,
        operator: PandaBinaryOperator
    ): UExpr<UAddressSort> {

    }
}

fun PandaUExprWrapper.withSort(ctx: PandaContext, sort: USort): PandaUExprWrapper {
    val newUExpr = when (from) {
        is PandaNumberConstant -> from.withSort(ctx, sort, uExpr)
        else -> uExpr
    }

    return PandaUExprWrapper(from, newUExpr)
}

fun PandaNumberConstant.withSort(
    ctx: PandaContext,
    sort: USort,
    default: UExpr<out USort>,
): UExpr<out USort> = when (sort) {
    is PandaBoolSort -> ctx.mkBool(value == 1)
    else -> default
}