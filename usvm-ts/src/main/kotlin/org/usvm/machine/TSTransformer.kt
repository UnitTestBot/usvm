package org.usvm.machine

import org.jacodb.ets.base.EtsType
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.expr.TSConcreteString
import org.usvm.machine.expr.TSStringSort
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator

interface TSTransformer : UTransformer<EtsType, TSSizeSort> {
    fun transform(expr: TSConcreteString): UExpr<TSStringSort>
}

class TSComposer(
    ctx: UContext<TSSizeSort>,
    memory: UReadOnlyMemory<EtsType>,
    ownership: MutabilityOwnership,
) : UComposer<EtsType, TSSizeSort>(ctx, memory, ownership), TSTransformer {
    override fun transform(expr: TSConcreteString): UExpr<TSStringSort> = expr
}

class TSExprTranslator(ctx: UContext<TSSizeSort>) : UExprTranslator<EtsType, TSSizeSort>(ctx), TSTransformer {
    override fun transform(expr: TSConcreteString): UExpr<TSStringSort> {
        error("Should not be called for concrete strings, only symbolic strings are allowed")
    }
}

