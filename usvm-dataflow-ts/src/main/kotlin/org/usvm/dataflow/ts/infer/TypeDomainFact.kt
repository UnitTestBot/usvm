package org.usvm.dataflow.ts.infer

import analysis.type.EtsTypeFact

sealed interface BackwardTypeDomainFact {
    data object Zero : BackwardTypeDomainFact

    data class TypedVariable(
        val variable: AccessPathBase,
        val type: EtsTypeFact,
    ) : BackwardTypeDomainFact
}

sealed interface ForwardTypeDomainFact {
    data object Zero : ForwardTypeDomainFact

    data class TypedVariable(
        val variable: AccessPath,
        val type: EtsTypeFact,
    ) : ForwardTypeDomainFact
}
