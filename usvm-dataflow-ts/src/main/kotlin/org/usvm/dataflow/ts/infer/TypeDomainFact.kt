package org.usvm.dataflow.ts.infer

sealed interface BackwardTypeDomainFact {
    data object Zero : BackwardTypeDomainFact

    // Requirement
    data class TypedVariable(
        val variable: AccessPathBase,
        val type: EtsTypeFact,
    ) : BackwardTypeDomainFact

    fun fixThis(): BackwardTypeDomainFact {
        if (this is TypedVariable && variable is AccessPathBase.Local && variable.name == "this") {
            return copy(variable = AccessPathBase.This)
        }
        return this
    }
}

sealed interface ForwardTypeDomainFact {
    data object Zero : ForwardTypeDomainFact

    // Exact type
    data class TypedVariable(
        val variable: AccessPath,
        val type: EtsTypeFact, // primitive or Object
    ) : ForwardTypeDomainFact

    fun fixThis(): ForwardTypeDomainFact {
        if (this is TypedVariable && variable.base is AccessPathBase.Local && variable.base.name == "this") {
            return copy(variable = variable.copy(base = AccessPathBase.This))
        }
        return this
    }
}
