package org.usvm.dataflow.ts.infer

sealed interface BackwardTypeDomainFact {
    data object Zero : BackwardTypeDomainFact

    data class TypedVariable(
        val variable: AccessPath,
        val type: EtsTypeFact,
    ) : BackwardTypeDomainFact
}

sealed interface ForwardTypeDomainFact {
    data object Zero : ForwardTypeDomainFact

    // Exact type
    data class TypedVariable(
        val variable: AccessPath,
        val type: EtsTypeFact, // primitive or Object
    ) : ForwardTypeDomainFact
}
