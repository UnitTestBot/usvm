package org.usvm.machine

import org.jacodb.ets.model.EtsType
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UTransformer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemory
import org.usvm.solver.UExprTranslator

interface TsTransformer : UTransformer<EtsType, TsSizeSort>

class TsComposer(
    ctx: UContext<TsSizeSort>,
    memory: UReadOnlyMemory<EtsType>,
    ownership: MutabilityOwnership,
) : UComposer<EtsType, TsSizeSort>(ctx, memory, ownership), TsTransformer

class TsExprTranslator(
    ctx: UContext<TsSizeSort>,
) : UExprTranslator<EtsType, TsSizeSort>(ctx), TsTransformer
