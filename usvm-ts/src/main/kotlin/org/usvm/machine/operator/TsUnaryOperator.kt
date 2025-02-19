package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext
import org.usvm.machine.expr.mkNumericExpr
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.interpreter.TsStepScope

sealed interface TsUnaryOperator {

    fun TsContext.resolveBool(
        arg: UBoolExpr,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.resolveFp(
        arg: UExpr<KFp64Sort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.resolveRef(
        arg: UExpr<UAddressSort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.resolveFake(
        arg: UConcreteHeapRef,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.resolve(
        arg: UExpr<out USort>,
        scope: TsStepScope,
    ): UExpr<out USort> {
        if (arg.isFakeObject()) {
            return resolveFake(arg, scope)
        }
        return when (arg.sort) {
            boolSort -> resolveBool(arg.asExpr(boolSort), scope)
            fp64Sort -> resolveFp(arg.asExpr(fp64Sort), scope)
            addressSort -> resolveRef(arg.asExpr(addressSort), scope)
            else -> TODO("Unsupported sort: ${arg.sort}")
        }
    }

    data object Not : TsUnaryOperator {
        override fun TsContext.resolveBool(
            arg: UBoolExpr,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkNot(arg)
        }

        override fun TsContext.resolveFp(
            arg: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkNot(mkTruthyExpr(arg, scope))
        }

        override fun TsContext.resolveRef(
            arg: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkNot(mkTruthyExpr(arg, scope))
        }

        override fun TsContext.resolveFake(
            arg: UConcreteHeapRef,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkNot(mkTruthyExpr(arg, scope))
        }
    }

    data object Neg : TsUnaryOperator {
        override fun TsContext.resolveBool(
            arg: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return mkFpNegationExpr(mkNumericExpr(arg, scope))
        }

        override fun TsContext.resolveFp(
            arg: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return mkFpNegationExpr(arg)
        }

        override fun TsContext.resolveRef(
            arg: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return mkFpNegationExpr(mkNumericExpr(arg, scope))
        }

        override fun TsContext.resolveFake(
            arg: UConcreteHeapRef,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return mkFpNegationExpr(mkNumericExpr(arg, scope))
        }
    }
}
