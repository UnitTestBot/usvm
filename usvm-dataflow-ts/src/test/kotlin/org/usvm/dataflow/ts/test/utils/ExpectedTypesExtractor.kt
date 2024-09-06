package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnknownType
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

        if (thisType != null && other.thisFact != null) {
            if (!other.thisFact.matchesWith(thisType)) return false
        }

        for ((i, fact) in other.argumentsFacts.withIndex()) {
            if (!fact.matchesWith(argumentsTypes[i])) return false
        }

        if (ignoreReturnType) return true

        return other.returnFact.matchesWith(returnType)
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

private fun EtsTypeFact.matchesWith(type: EtsType): Boolean = when (this) {
    is EtsTypeFact.ObjectEtsTypeFact -> {
        // TODO it should be replaced with signatures
        val typeName = this.cls?.typeName
        (type is EtsClassType || type is EtsUnclearRefType) && type.typeName == typeName
    }

    EtsTypeFact.AnyEtsTypeFact -> type is EtsAnyType
    is EtsTypeFact.ArrayEtsTypeFact -> when (type) {
        is EtsArrayType -> this.elementType.matchesWith(type.elementType)
        is EtsUnclearRefType -> TODO()
        else -> false
    }

    EtsTypeFact.BooleanEtsTypeFact -> type is EtsBooleanType
    EtsTypeFact.FunctionEtsTypeFact -> type is EtsFunctionType
    EtsTypeFact.NullEtsTypeFact -> type is EtsNullType
    EtsTypeFact.NumberEtsTypeFact -> type is EtsNumberType
    EtsTypeFact.StringEtsTypeFact -> type is EtsStringType
    EtsTypeFact.UndefinedEtsTypeFact -> type is EtsUndefinedType
    EtsTypeFact.UnknownEtsTypeFact -> type is EtsUnknownType
    is EtsTypeFact.GuardedTypeFact -> TODO()
    is EtsTypeFact.IntersectionEtsTypeFact -> TODO()
    is EtsTypeFact.UnionEtsTypeFact -> TODO()
}