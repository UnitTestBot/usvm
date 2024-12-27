package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
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
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.util.MethodTypesFacts
import java.io.File

private val logger = mu.KotlinLogging.logger {}

class ExpectedTypesExtractor(private val graph: EtsApplicationGraph) {
    fun extractTypes(method: EtsMethod): MethodTypes {
        val returnType = method.returnType
        val argumentsTypes = method.parameters.map { it.type }
        val thisType = getEtsClassType(method, graph)

        return MethodTypes(thisType, argumentsTypes, returnType)
    }

}

private fun getEtsClassType(method: EtsMethod, graph: EtsApplicationGraph) =
    if (method.enclosingClass.name == DEFAULT_ARK_CLASS_NAME || method.enclosingClass.name.isBlank()) {
        null
    } else {
        val clazz = graph.cp
            .projectAndSdkClasses
            // .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
            // TODO different representation in abc and ast, replace with signatures
            // .singleOrNull { it.name == method.enclosingClass.name }
            // ?: error("TODO")
            .firstOrNull { it.name == method.enclosingClass.name }
            ?: error("") /*return MethodTypes(null, argumentsTypes, returnType)*/

        EtsClassType(clazz.signature)
    }

class ClassMatcherStatistics {
    private val overallTypes: Long
        get() = overallThisTypes + overallArgsTypes + overallReturnTypes
    private val matched: Long
        get() = exactlyMatchedThisTypes + exactlyMatchedArgsTypes + exactlyMatchedReturnTypes
    private val someFactsFound: Long
        get() = someFactsAboutThisTypes + someFactsAboutArgsTypes + someFactsAboutReturnTypes + returnIsAnyType + argIsAnyType

    private var failedMethods: MutableList<EtsMethod> = mutableListOf()

    private var overallThisTypes: Long = 0L
    private var exactlyMatchedThisTypes: Long = 0L
    private var someFactsAboutThisTypes: Long = 0L

    private var overallArgsTypes: Long = 0L
    private var exactlyMatchedArgsTypes: Long = 0L
    private var someFactsAboutArgsTypes: Long = 0L
    private var argIsAnyType: Long = 0L

    private var overallReturnTypes: Long = 0L
    private var exactlyMatchedReturnTypes: Long = 0L
    private var someFactsAboutReturnTypes: Long = 0L
    private var returnIsAnyType: Long = 0L

    private val methodToTypes: MutableMap<EtsMethod, MutableMap<AccessPathBase, Pair<EtsType, EtsTypeFact?>>> =
        hashMapOf()
    private val methodToReturnTypes: MutableMap<EtsMethod, Pair<EtsType, EtsTypeFact?>> = hashMapOf()

    private fun EtsMethod.saveComparisonInfo(
        position: AccessPathBase,
        type: EtsType,
        fact: EtsTypeFact?,
    ) {
        val methodTypes = methodToTypes.getOrPut(this) { hashMapOf() }
        check(position !in methodTypes)
        methodTypes[position] = type to fact
    }

    fun calculateStats(
        methodResults: MethodTypesFacts,
        types: MethodTypes?,
        scene: EtsScene,
        astMethod: EtsMethod,
        abcMethod: EtsMethod,
        astGraph: EtsApplicationGraph,
        abcGraph: EtsApplicationGraph,
    ) {
        methodResults.apply {
            if (combinedThisFact == null && argumentsFacts.all { it == null } && returnFact == null && localFacts.isEmpty()) {
                saveAbsentResult(astMethod)
                return
            }
        }

        compareTypesWithExpected(
            methodResults,
            requireNotNull(types),
            scene,
            astMethod
        )

    }

    private fun compareTypesWithExpected(
        facts: MethodTypesFacts,
        types: MethodTypes,
        scene: EtsScene,
        method: EtsMethod,
    ) {
        // 'this' type
        types.thisType?.let {
            overallThisTypes++

            method.saveComparisonInfo(AccessPathBase.This, it, facts.combinedThisFact)

            val thisFact = facts.combinedThisFact ?: return@let
            if (thisFact.matchesWith(it, strictMode = false)) {
                exactlyMatchedThisTypes++
            } else {
                someFactsAboutThisTypes++
            }
        }

        // args
        types.argumentsTypes.forEachIndexed { index, type ->
            overallArgsTypes++

            val fact = facts.argumentsFacts.getOrNull(index)


            method.saveComparisonInfo(AccessPathBase.Arg(index), type, fact)

            if (fact is EtsTypeFact.AnyEtsTypeFact) {
                argIsAnyType++
                return@forEachIndexed
            }

            if (fact == null) return@forEachIndexed

            if (fact.matchesWith(type, strictMode = false)) {
                exactlyMatchedArgsTypes++
            } else {
                someFactsAboutArgsTypes++
            }
        }

        // return type
        val inferredReturnType = facts.returnFact ?: EtsTypeFact.AnyEtsTypeFact

        overallReturnTypes++
        methodToReturnTypes[method] = method.returnType to inferredReturnType

        if (inferredReturnType is EtsTypeFact.AnyEtsTypeFact) {
            returnIsAnyType++
            return
        }

        if (inferredReturnType.matchesWith(types.returnType, strictMode = false)) {
            exactlyMatchedReturnTypes++
        } else if (inferredReturnType.partialMatchedBy(types.returnType)) {
            someFactsAboutReturnTypes++
        }
    }

    fun saveAbsentResult(method: EtsMethod) {
        failedMethods += method
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
        Any type as arg: $argIsAnyType
        Not found: ${overallArgsTypes - exactlyMatchedArgsTypes - someFactsAboutArgsTypes - argIsAnyType}
        
        Return types total: $overallReturnTypes
        Exactly matched return types: $exactlyMatchedReturnTypes
        Partially matched return types: $someFactsAboutReturnTypes
        Any type is returned: $returnIsAnyType
        Not found: ${overallReturnTypes - exactlyMatchedReturnTypes - someFactsAboutReturnTypes - returnIsAnyType}
        
        Didn't find any types for ${failedMethods.size} methods
    """.trimIndent()

    fun dumpStatistics(outputFilePath: String? = null) {
        val data = buildString {
            appendLine(this@ClassMatcherStatistics.toString())
            appendLine()

            appendLine("Specifically: ${"=".repeat(42)}")

            val comparator =
                Comparator<MutableMap.MutableEntry<AccessPathBase, Pair<EtsType, EtsTypeFact?>>> { fst, snd ->
                    when (fst.key) {
                        is AccessPathBase.This -> when {
                            snd.key is AccessPathBase.This -> 0
                            else -> -1
                        }

                        is AccessPathBase.Arg -> when (snd.key) {
                            is AccessPathBase.This -> 1
                            is AccessPathBase.Arg -> {
                                (fst.key as AccessPathBase.Arg).index.compareTo((snd.key as AccessPathBase.Arg).index)
                            }

                            else -> -1
                        }

                        else -> when (snd.key) {
                            is AccessPathBase.This, is AccessPathBase.Arg -> 1
                            else -> 0
                        }
                    }
                }

            methodToTypes.forEach { (method, types) ->
                appendLine("${method.signature}:")

                types
                    .entries
                    .sortedWith(comparator)
                    .forEach { (path, typeInfo) ->
                        appendLine("${path}: ${typeInfo.first} -> ${typeInfo.second}")
                    }
                appendLine()
            }

            appendLine()
            appendLine("=".repeat(42))
            appendLine("Failed methods:")
            failedMethods.forEach {
                appendLine(it)
            }
        }

        if (outputFilePath == null) {
            println(data)
            return
        }

        val file = File(outputFilePath)
        println("File with statistics is located: ${file.absolutePath}")
        file.writeText(data)
    }
}

data class MethodTypes(
    val thisType: EtsType?,
    val argumentsTypes: List<EtsType>,
    val returnType: EtsType,
) {
    fun matchesWithTypeFacts(other: MethodTypesFacts, ignoreReturnType: Boolean, scene: EtsScene): Boolean {
        if (thisType == null && other.combinedThisFact != null) return false

        if (thisType != null && other.combinedThisFact != null) {
            if (!other.combinedThisFact.matchesWith(thisType, strictMode = false)) return false
        }

        for ((i, fact) in other.argumentsFacts.withIndex()) {
            if (!fact.matchesWith(argumentsTypes[i], strictMode = false)) return false
        }

        if (ignoreReturnType) return true

        return other.returnFact.matchesWith(returnType, strictMode = false)
    }
}

private fun EtsTypeFact?.matchesWith(type: EtsType, strictMode: Boolean): Boolean {
    val result = when (this) {
        null, EtsTypeFact.AnyEtsTypeFact -> {
            // TODO any other combination?
            type is EtsAnyType || (!strictMode && type is EtsUnknownType)
        }

        is EtsTypeFact.ObjectEtsTypeFact -> {
            // TODO it should be replaced with signatures
            val typeName = this.cls?.typeName

            if ((type is EtsUnknownType || type is EtsAnyType) && !strictMode) {
                this.cls != null
            } else {
                (type is EtsClassType || type is EtsUnclearRefType) && type.typeName == typeName
            }
        }

        is EtsTypeFact.ArrayEtsTypeFact -> when (type) {
            is EtsArrayType -> this.elementType.matchesWith(type.elementType, strictMode)

            is EtsUnclearRefType -> {
                val elementType = this.elementType as? EtsTypeFact.ObjectEtsTypeFact
                elementType?.cls?.typeName == type.typeName
            }

            else -> false
        }

        EtsTypeFact.BooleanEtsTypeFact -> {
            type is EtsBooleanType
                || (type is EtsUnknownType && !strictMode)
                || (type as? EtsClassType)?.typeName == "Boolean"
                || (type as? EtsUnclearRefType)?.typeName == "Boolean"
        }

        EtsTypeFact.FunctionEtsTypeFact -> type is EtsFunctionType || (type is EtsUnknownType && !strictMode)
        EtsTypeFact.NullEtsTypeFact -> type is EtsNullType || (type is EtsUnknownType && !strictMode)
        EtsTypeFact.NumberEtsTypeFact -> {
            type is EtsNumberType
                || (type is EtsUnknownType && !strictMode)
                || (type as? EtsClassType)?.typeName == "Number"
                || (type as? EtsUnclearRefType)?.typeName == "Number"
        }

        EtsTypeFact.StringEtsTypeFact -> {
            type is EtsStringType
                || (type is EtsUnknownType && !strictMode)
                || (type as? EtsClassType)?.typeName == "String"
                || (type as? EtsUnclearRefType)?.typeName == "String"
        }

        EtsTypeFact.UndefinedEtsTypeFact -> type is EtsUndefinedType
        EtsTypeFact.UnknownEtsTypeFact -> type is EtsUnknownType
        is EtsTypeFact.GuardedTypeFact -> TODO()
        is EtsTypeFact.IntersectionEtsTypeFact -> {
            // TODO intersections checks are not supported yet
            false
        }

        is EtsTypeFact.UnionEtsTypeFact -> if (strictMode) {
            types.all { it.matchesWith(type, strictMode) }
        } else {
            types.any { it.matchesWith(type, strictMode) }
        }
    }

    if (!result) {
        logger.warn {
            """
                Fact: $this
                Type: $type
                
            """.trimIndent()
        }
    }

    return result
}

private fun EtsTypeFact.partialMatchedBy(type: EtsType): Boolean {
    if (type is EtsUnknownType) return true
    logger.warn { "Not implemented partial match for fact $this and type $type" }
    return false
}
