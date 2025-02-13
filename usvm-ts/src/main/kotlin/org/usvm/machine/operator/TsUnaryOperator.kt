package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.interpreter.TsStepScope

sealed interface TsUnaryOperator {

    fun TsContext.resolveBool(
        arg: UExpr<UBoolSort>,
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
            arg: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkNot(arg)
        }

        override fun TsContext.resolveFp(
            arg: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkNot(mkTruthyExpr(arg, scope))
        }

        override fun TsContext.resolveRef(
            arg: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkNot(mkTruthyExpr(arg, scope))
        }

        override fun TsContext.resolveFake(
            arg: UConcreteHeapRef,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    data object Neg : TsUnaryOperator {
        override fun TsContext.resolveBool(
            arg: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            // -true = -1.0
            // -false = -0.0
            @Suppress("MagicNumber")
            return mkIte(arg, mkFp64(-1.0), mkFp64(-0.0))
        }

        override fun TsContext.resolveFp(
            arg: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpNegationExpr(arg)
        }

        override fun TsContext.resolveRef(
            arg: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            // -undefined = NaN
            if (arg == mkUndefinedValue()) {
                return mkFp64NaN()
            }

            // TODO: convert to numeric value and then negate
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFake(
            arg: UConcreteHeapRef,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }
}
