package org.usvm.machine

import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UTransformer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.TsType
import org.usvm.solver.UExprTranslator

interface TsTransformer : UTransformer<TsType, TsSizeSort>

class TsComposer(
    ctx: UContext<TsSizeSort>,
    memory: UReadOnlyMemory<TsType>,
    ownership: MutabilityOwnership,
) : UComposer<TsType, TsSizeSort>(ctx, memory, ownership), TsTransformer

class TsExprTranslator(
    ctx: UContext<TsSizeSort>,
) : UExprTranslator<TsType, TsSizeSort>(ctx), TsTransformer
