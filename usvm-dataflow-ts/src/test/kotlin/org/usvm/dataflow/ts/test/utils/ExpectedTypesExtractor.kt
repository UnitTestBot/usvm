package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsMethodTypeFacts
import org.usvm.dataflow.ts.infer.EtsTypeFact

class ExpectedTypesExtractor(private val applicationGraph: EtsApplicationGraph) {
    fun extractTypes(method: EtsMethod): MethodTypes {
        val returnType = method.returnType
        val argumentsTypes = method.parameters.map { it.type }
        val thisType = if (method.enclosingClass.name == "_DEFAULT_ARK_CLASS") {
            null
        } else {
            val clazz = applicationGraph.cp
                .classes
                .singleOrNull { it.signature == method.enclosingClass }
                ?: error("TODO")

            EtsClassType(clazz.signature)
        }

        return MethodTypes(thisType, argumentsTypes, returnType)
    }
}

data class MethodTypes(
    val thisType: EtsType?,
    val argumentsTypes: List<EtsType>,
    val returnType: EtsType
) {
    fun matchesWithTypeFacts(other: MethodTypesFacts, ignoreReturnType: Boolean): Boolean {
        if (thisType == null && other.thisFact != null) return false

        if (thisType != null) {
            TODO()
        }

        TODO()
    }
}

data class MethodTypesFacts(
    val thisFact: EtsTypeFact?,
    val argumentsFacts: List<EtsTypeFact>,
    val returnFact: EtsTypeFact
) {
    companion object {
        fun fromEtsMethodTypeFacts(fact: EtsMethodTypeFacts): MethodTypesFacts {
            val types = fact.types

            val thisType = types[AccessPathBase.This]
            val arguments = fact.method
                .parameters
                .indices
                .map { types[AccessPathBase.Arg(it)] ?: EtsTypeFact.AnyEtsTypeFact }

            return MethodTypesFacts(thisType, arguments, EtsTypeFact.AnyEtsTypeFact /* TODO replace it */)
        }
    }
}