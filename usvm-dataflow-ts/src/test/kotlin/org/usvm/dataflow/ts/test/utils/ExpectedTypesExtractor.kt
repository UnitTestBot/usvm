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
import org.jacodb.ets.model.EtsScene
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
                .filterNot { it.name.startsWith("AnonymousClass-") }
//                .singleOrNull { it.name == method.enclosingClass.name } // TODO different representation in abc and ast, replace with signatures
//                ?: error("TODO")
                .firstOrNull { it.name == method.enclosingClass.name } ?: error("TODO")

            EtsClassType(clazz.signature)
        }

        return MethodTypes(thisType, argumentsTypes, returnType)
    }
}

class ClassMatcherStatistics {
    private val overallTypes: Long
        get() = overallThisTypes + overallArgsTypes + overallReturnTypes
    private val matched: Long
        get() = exactlyMatchedThisTypes + exactlyMatchedArgsTypes + exactlyMatchedReturnTypes
    private val someFactsFound: Long
        get() = someFactsAboutThisTypes + someFactsAboutArgsTypes + someFactsAboutReturnTypes + returnIsAnyType

    private var overallThisTypes: Long = 0L
    private var exactlyMatchedThisTypes: Long = 0L
    private var someFactsAboutThisTypes: Long = 0L

    private var overallArgsTypes: Long = 0L
    private var exactlyMatchedArgsTypes: Long = 0L
    private var someFactsAboutArgsTypes: Long = 0L

    private var overallReturnTypes: Long = 0L
    private var exactlyMatchedReturnTypes: Long = 0L
    private var someFactsAboutReturnTypes: Long = 0L
    private var returnIsAnyType: Long = 0L

    fun verify(facts: MethodTypesFacts, types: MethodTypes, scene: EtsScene) {
        // 'this' type
        types.thisType?.let {
            overallThisTypes++

            val thisFact = facts.thisFact ?: return@let
            if (thisFact.matchesWith(it, scene)) {
                exactlyMatchedThisTypes++
            } else {
                someFactsAboutThisTypes++
            }
        }

        // args
        types.argumentsTypes.forEachIndexed { index, type ->
            overallArgsTypes++

            val fact = facts.argumentsFacts.getOrNull(index) ?: return@forEachIndexed
            if (fact.matchesWith(type, scene)) {
                exactlyMatchedArgsTypes++
            } else {
                someFactsAboutArgsTypes++
            }
        }

        // return type
        overallReturnTypes++

        if (facts.returnFact is EtsTypeFact.AnyEtsTypeFact) {
            returnIsAnyType++
            return
        }

        if (facts.returnFact.matchesWith(types.returnType, scene)) {
            exactlyMatchedReturnTypes++
        } else if (facts.returnFact.partialMatchedBy(types.returnType)) {
            someFactsAboutReturnTypes++
        }
    }

    override fun toString(): String = """
        Total types number: $overallTypes
        Exactly matched: $matched
        Partially matched: $someFactsFound
        Not found: ${overallTypes - matched - someFactsFound}
        
        Specifically: 
        
        This types total: $overallThisTypes
        Exactly matched this types: $exactlyMatchedThisTypes
        Partially matched this types: $someFactsAboutThisTypes
        Not found: ${overallThisTypes - exactlyMatchedThisTypes - someFactsAboutThisTypes}
        
        Args types total: $overallArgsTypes
        Exactly matched args types: $exactlyMatchedArgsTypes
        Partially matched args types: $someFactsAboutArgsTypes
        Not found: ${overallArgsTypes - exactlyMatchedArgsTypes - someFactsAboutArgsTypes}
        
        Return types total: $overallReturnTypes
        Exactly matched return types: $exactlyMatchedReturnTypes
        Partially matched return types: $someFactsAboutReturnTypes
        Any type is returned: $returnIsAnyType
        Not found: ${overallReturnTypes - exactlyMatchedReturnTypes - someFactsAboutReturnTypes - returnIsAnyType}
    """.trimIndent()
}

data class MethodTypes(
    val thisType: EtsType?,
    val argumentsTypes: List<EtsType>,
    val returnType: EtsType
) {
    fun matchesWithTypeFacts(other: MethodTypesFacts, ignoreReturnType: Boolean, scene: EtsScene): Boolean {
        if (thisType == null && other.thisFact != null) return false

        if (thisType != null && other.thisFact != null) {
            if (!other.thisFact.matchesWith(thisType, scene)) return false
        }

        for ((i, fact) in other.argumentsFacts.withIndex()) {
            if (!fact.matchesWith(argumentsTypes[i], scene)) return false
        }

        if (ignoreReturnType) return true

        return other.returnFact.matchesWith(returnType, scene)
    }
}

data class MethodTypesFacts(
    val thisFact: EtsTypeFact?,
    val argumentsFacts: List<EtsTypeFact>,
    val returnFact: EtsTypeFact
) {
    companion object {
        fun fromEtsMethodTypeFacts(fact: EtsMethodTypeFacts, method: EtsMethod): MethodTypesFacts {
            val types = fact.types

            val thisType = types[AccessPathBase.This]
            val arguments = method
                .parameters
                .indices
                .map { types[AccessPathBase.Arg(it)] ?: EtsTypeFact.AnyEtsTypeFact }

            return MethodTypesFacts(thisType, arguments, EtsTypeFact.AnyEtsTypeFact /* TODO replace it */)
        }
    }
}

private fun EtsTypeFact.matchesWith(type: EtsType, scene: EtsScene): Boolean = when (this) {
    is EtsTypeFact.ObjectEtsTypeFact -> {
        // TODO it should be replaced with signatures
        val typeName = this.cls?.typeName
        (type is EtsClassType || type is EtsUnclearRefType) && type.typeName == typeName
    }

    EtsTypeFact.AnyEtsTypeFact -> {
        type is EtsAnyType || type is EtsUnknownType // TODO any other combination?
    }
    is EtsTypeFact.ArrayEtsTypeFact -> when (type) {
        is EtsArrayType -> this.elementType.matchesWith(type.elementType, scene)

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
    is EtsTypeFact.IntersectionEtsTypeFact -> {
        // TODO intersections checks are not supported yet
        false
    }
    is EtsTypeFact.UnionEtsTypeFact -> types.any { it.matchesWith(type, scene)
    }
}

private fun EtsTypeFact.partialMatchedBy(type: EtsType): Boolean {
    if (type is EtsUnknownType) return true
    TODO()
}