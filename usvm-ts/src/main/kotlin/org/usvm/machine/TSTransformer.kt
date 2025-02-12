package org.usvm.machine

import org.jacodb.ets.base.EtsType
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UTransformer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator

interface TSTransformer : UTransformer<EtsType, TSSizeSort>

class TSComposer(
    ctx: UContext<TSSizeSort>,
    memory: UReadOnlyMemory<EtsType>,
    ownership: MutabilityOwnership,
) : UComposer<EtsType, TSSizeSort>(ctx, memory, ownership), TSTransformer

class TSExprTranslator(ctx: UContext<TSSizeSort>) : UExprTranslator<EtsType, TSSizeSort>(ctx), TSTransformer
