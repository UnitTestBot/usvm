package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod

data class EtsMethodTypeFacts(
    val method: EtsMethod,
    val types: Map<AccessPathBase, EtsTypeFact>,
)
