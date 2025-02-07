package org.usvm.machine

import io.ksmt.utils.mkConst
import org.jacodb.ets.base.EtsType
import org.usvm.UAddressSort
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UTransformer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.expr.TSNullRefExpr
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator

interface TSTransformer : UTransformer<EtsType, TSSizeSort> {
    fun transform(expr: TSNullRefExpr): UExpr<UAddressSort>
}

class TSComposer(
    ctx: UContext<TSSizeSort>,
    memory: UReadOnlyMemory<EtsType>,
    ownership: MutabilityOwnership,
) : UComposer<EtsType, TSSizeSort>(ctx, memory, ownership), TSTransformer {
    override fun transform(expr: TSNullRefExpr): UExpr<UAddressSort> {
        return when (val memory = memory) {
            is TSModel -> memory.tsNullRef
            else -> (ctx as TSContext).mkTSNullRefValue()
        }
    }
}

class TSExprTranslator(ctx: UContext<TSSizeSort>) : UExprTranslator<EtsType, TSSizeSort>(ctx), TSTransformer {
    override fun transform(expr: TSNullRefExpr): UExpr<UAddressSort> = expr.sort.mkConst("TsNullRef")
}

