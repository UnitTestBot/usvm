/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.getLocals
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.toBase
import org.usvm.dataflow.ts.util.getRealLocals
import java.io.File

class TypeInferenceStatistics {
    private val noTypesInferred: MutableSet<EtsMethod> = hashSetOf()
    private val allTypesAndFacts: MutableMap<EtsMethod, MutableList<InferenceResult>> =
        hashMapOf()

    private var overallTypes: Long = 0

    private var exactTypeInferredPreviouslyUnknown = 0L
    private var exactTypeInferredCorrectlyPreviouslyKnown = 0L
    private var exactTypeInferredPreviouslyWasAny = 0L
    private var exactTypeInferredIncorrectlyPreviouslyKnown = 0L

    private var typeInfoInferredPreviouslyUnknown = 0L
    private var typeInfoInferredPreviouslyKnownExactly = 0L
    private var arrayInfoPreviouslyUnknown = 0L
    private var arrayInfoPreviouslyKnown = 0L

    private var noInfoInferredPreviouslyKnown = 0L
    private var noInfoInferredPreviouslyUnknown = 0L

    private var unhandled = 0L

    private var undefinedUnknownCombination = 0L
    private var unknownAnyCombination = 0L

    private var knownTypeToUndefined = 0L

    fun compareSingleMethodFactsWithTypesInScene(
        methodTypeFacts: MethodTypesFacts,
        method: EtsMethod,
        graph: EtsApplicationGraph,
    ) {
        overallTypes += 1 // thisType
        overallTypes += method.parameters.size + method.getLocals().size

        methodTypeFacts.apply {
            if (combinedThisFact == null
                && argumentsFacts.all { it == null }
                && localFacts.isEmpty()
            ) {
                noTypesInferred += method
                // Note: no return here!
                // Without taking into account the stats for such "empty" methods,
                // the statistic would not be correct.
            }
        }

        val thisType = graph.cp.getEtsClassType(method.signature.enclosingClass)
        val argTypes = method.parameters.map { it.type }
        val locals = method.getRealLocals().filterNot { it.name == "this" }

        val methodFacts = mutableListOf<InferenceResult>()

        thisType?.let {
            val fact = methodTypeFacts.combinedThisFact

            val status = if (fact == null) {
                // TODO check how unknown is represented
                if (it.signature.name == "Unknown") {
                    noInfoInferredPreviouslyUnknown++
                    InferenceStatus.NO_INFO_PREVIOUSLY_UNKNOWN
                } else {
                    noInfoInferredPreviouslyKnown++
                    InferenceStatus.NO_INFO_PREVIOUSLY_KNOWN
                }
            } else {
                when {
                    fact.matchesWith(it) -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    (fact as? EtsTypeFact.ObjectEtsTypeFact)?.cls != null -> {
                        exactTypeInferredIncorrectlyPreviouslyKnown++
                        InferenceStatus.DIFFERENT_TYPE_FOUND
                    }

                    else -> {
                        typeInfoInferredPreviouslyKnownExactly++
                        InferenceStatus.TYPE_INFO_FOUND_PREV_KNOWN
                    }
                }
            }

            methodFacts += InferenceResult(AccessPathBase.This, it, fact, status)
        }

        argTypes.forEachIndexed { index, type ->
            val fact = methodTypeFacts.argumentsFacts.getOrNull(index)

            val status = if (fact == null) {
                if (type is EtsUnknownType) {
                    noInfoInferredPreviouslyUnknown++
                    InferenceStatus.NO_INFO_PREVIOUSLY_UNKNOWN
                } else {
                    noInfoInferredPreviouslyKnown++
                    InferenceStatus.NO_INFO_PREVIOUSLY_KNOWN
                }
            } else {
                checkForFact(fact, type)
            }

            methodFacts += InferenceResult(AccessPathBase.Arg(index), type, fact, status)
        }

        locals.forEach {
            val realType = it.type
            val base = it.toBase()
            val fact = methodTypeFacts.localFacts[base]
            val status = if (fact == null) {
                if (realType is EtsUnknownType) {
                    noInfoInferredPreviouslyUnknown++
                    InferenceStatus.NO_INFO_PREVIOUSLY_UNKNOWN
                } else {
                    noInfoInferredPreviouslyKnown++
                    InferenceStatus.NO_INFO_PREVIOUSLY_KNOWN
                }
            } else {
                checkForFact(fact, realType)
            }
            methodFacts += InferenceResult(base, realType, fact, status)
        }

        allTypesAndFacts[method] = methodFacts
    }

    private fun checkForFact(fact: EtsTypeFact, type: EtsType): InferenceStatus {
        return when (fact) {
            EtsTypeFact.AnyEtsTypeFact -> {
                when (type) {
                    is EtsUnknownType -> {
                        unknownAnyCombination++
                        InferenceStatus.UNKNOWN_ANY_COMBINATION
                    }

                    is EtsAnyType -> {
                        exactTypeInferredPreviouslyWasAny++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_WAS_ANY
                    }

                    else -> {
                        exactTypeInferredIncorrectlyPreviouslyKnown++
                        InferenceStatus.DIFFERENT_TYPE_FOUND
                    }
                }
            }

            is EtsTypeFact.ArrayEtsTypeFact -> {
                when (type) {
                    is EtsArrayType -> {
                        arrayInfoPreviouslyKnown++
                        InferenceStatus.ARRAY_INFO
                    }

                    is EtsUnclearRefType -> {
                        arrayInfoPreviouslyKnown++
                        InferenceStatus.ARRAY_INFO
                    }

                    is EtsUnknownType -> {
                        arrayInfoPreviouslyUnknown++
                        InferenceStatus.ARRAY_INFO_PREV_UNKNOWN
                    }

                    else -> {
                        arrayInfoPreviouslyKnown++
                        InferenceStatus.ARRAY_INFO
                    }
                }
            }

            EtsTypeFact.BooleanEtsTypeFact -> {
                when (type) {
                    is EtsBooleanType -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    is EtsUnknownType -> {
                        exactTypeInferredPreviouslyUnknown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_UNKNOWN
                    }

                    else -> {
                        when {
                            (type as? EtsClassType)?.typeName == "Boolean" -> {
                                exactTypeInferredCorrectlyPreviouslyKnown++
                                InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                            }

                            (type as? EtsUnclearRefType)?.typeName == "Boolean" -> {
                                exactTypeInferredCorrectlyPreviouslyKnown++
                                InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                            }

                            else -> {
                                exactTypeInferredIncorrectlyPreviouslyKnown++
                                InferenceStatus.DIFFERENT_TYPE_FOUND
                            }
                        }
                    }
                }
            }

            EtsTypeFact.FunctionEtsTypeFact -> {
                when (type) {
                    is EtsUnknownType -> {
                        undefinedUnknownCombination++
                        InferenceStatus.UNKNOWN_UNDEFINED_COMBINATION
                    }

                    is EtsFunctionType -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    else -> {
                        exactTypeInferredIncorrectlyPreviouslyKnown++
                        InferenceStatus.DIFFERENT_TYPE_FOUND
                    }
                }
            }

            EtsTypeFact.NullEtsTypeFact -> {
                when (type) {
                    is EtsUnknownType -> {
                        undefinedUnknownCombination++
                        InferenceStatus.UNKNOWN_UNDEFINED_COMBINATION
                    }

                    is EtsNullType -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    else -> {
                        exactTypeInferredIncorrectlyPreviouslyKnown++
                        InferenceStatus.DIFFERENT_TYPE_FOUND
                    }
                }
            }

            EtsTypeFact.NumberEtsTypeFact -> {
                when (type) {
                    is EtsNumberType -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    is EtsUnknownType -> {
                        exactTypeInferredPreviouslyUnknown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_UNKNOWN
                    }

                    else -> {
                        when {
                            (type as? EtsClassType)?.typeName == "Number" -> {
                                exactTypeInferredCorrectlyPreviouslyKnown++
                                InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                            }

                            (type as? EtsUnclearRefType)?.typeName == "Number" -> {
                                exactTypeInferredCorrectlyPreviouslyKnown++
                                InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                            }

                            else -> {
                                exactTypeInferredIncorrectlyPreviouslyKnown++
                                InferenceStatus.DIFFERENT_TYPE_FOUND
                            }
                        }
                    }
                }
            }

            is EtsTypeFact.ObjectEtsTypeFact -> {
                if (type is EtsUnknownType) {
                    return if (fact.cls != null) {
                        exactTypeInferredPreviouslyUnknown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_UNKNOWN
                    } else {
                        typeInfoInferredPreviouslyUnknown++
                        InferenceStatus.TYPE_INFO_FOUND_PREV_UNKNOWN
                    }
                }

                if (type is EtsAnyType) {
                    return if (fact.cls != null) {
                        exactTypeInferredPreviouslyWasAny++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_WAS_ANY
                    } else {
                        typeInfoInferredPreviouslyKnownExactly++
                        InferenceStatus.TYPE_INFO_FOUND_PREV_KNOWN
                    }
                }

                if (fact.cls == null) {
                    return InferenceStatus.TYPE_INFO_FOUND_PREV_KNOWN
                }

                val typeName = fact.cls.typeName

                if ((type is EtsClassType || type is EtsUnclearRefType) && type.typeName == typeName) {
                    exactTypeInferredCorrectlyPreviouslyKnown++
                    InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                } else {
                    exactTypeInferredIncorrectlyPreviouslyKnown++
                    InferenceStatus.DIFFERENT_TYPE_FOUND
                }
            }

            EtsTypeFact.StringEtsTypeFact -> {
                when (type) {
                    is EtsStringType -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    is EtsUnknownType -> {
                        exactTypeInferredPreviouslyUnknown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_UNKNOWN
                    }

                    else -> {
                        when {
                            (type as? EtsClassType)?.typeName == "String" -> {
                                exactTypeInferredCorrectlyPreviouslyKnown++
                                InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                            }

                            (type as? EtsUnclearRefType)?.typeName == "String" -> {
                                exactTypeInferredCorrectlyPreviouslyKnown++
                                InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                            }

                            else -> {
                                exactTypeInferredIncorrectlyPreviouslyKnown++
                                InferenceStatus.DIFFERENT_TYPE_FOUND
                            }
                        }
                    }
                }
            }

            EtsTypeFact.UndefinedEtsTypeFact -> {
                when (type) {
                    is EtsUnknownType -> {
                        undefinedUnknownCombination++
                        InferenceStatus.UNKNOWN_UNDEFINED_COMBINATION
                    }

                    is EtsUndefinedType -> {
                        exactTypeInferredCorrectlyPreviouslyKnown++
                        InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                    }

                    else -> {
                        knownTypeToUndefined++
                        InferenceStatus.KNOWN_UNDEFINED_COMBINATION
                    }
                }
            }

            EtsTypeFact.UnknownEtsTypeFact -> {
                if (type is EtsUnknownType) {
                    exactTypeInferredCorrectlyPreviouslyKnown++
                    InferenceStatus.EXACT_MATCH_PREVIOUSLY_KNOWN
                } else {
                    exactTypeInferredIncorrectlyPreviouslyKnown++
                    InferenceStatus.DIFFERENT_TYPE_FOUND
                }
            }

            is EtsTypeFact.GuardedTypeFact -> {
                unhandled++
                InferenceStatus.UNHANDLED
            }

            is EtsTypeFact.IntersectionEtsTypeFact -> {
                unhandled++
                InferenceStatus.UNHANDLED
            }

            is EtsTypeFact.UnionEtsTypeFact -> {
                when (type) {
                    is EtsUnionType -> {
                        unhandled++
                        InferenceStatus.UNHANDLED
                    }

                    is EtsUnknownType -> {
                        InferenceStatus.UNION_INSTEAD_OF_UNKNOWN
                    }

                    else -> {
                        InferenceStatus.DIFFERENT_TYPE_FOUND
                    }
                }
            }
        }
    }

    fun dumpStatistics(outputFilePath: String? = null) {
        val data = buildString {
            appendLine(this@TypeInferenceStatistics.toString())
            appendLine()

            appendLine("Specifically: ${"=".repeat(42)}")

            val comparator =
                Comparator<InferenceResult> { fst, snd ->
                    when (fst.position) {
                        is AccessPathBase.This -> when {
                            snd.position is AccessPathBase.This -> 0
                            else -> -1
                        }

                        is AccessPathBase.Arg -> when (snd.position) {
                            is AccessPathBase.This -> 1
                            is AccessPathBase.Arg -> {
                                fst.position.index.compareTo(snd.position.index)
                            }

                            else -> -1
                        }

                        else -> when (snd.position) {
                            is AccessPathBase.This, is AccessPathBase.Arg -> 1
                            else -> if (fst.position is AccessPathBase.Local && snd.position is AccessPathBase.Local) {
                                fst.position.name.compareTo(snd.position.name)
                            } else {
                                0
                            }
                        }
                    }
                }

            allTypesAndFacts
                .toList()
                .sortedBy { it.first.signature.toString() }
                .forEach { (method, types) ->
                    appendLine("${method.signature}:")

                    types
                        .sortedWith(comparator)
                        .forEach { (path, typeInfo, typeFact, status) ->
                            appendLine("[${status.message}]: ${path}: $typeInfo -> $typeFact ")
                        }
                    appendLine()
                }

            appendLine()
            appendLine("=".repeat(42))
            appendLine("No types inferred for methods:")

            noTypesInferred
                .filterNot {
                    it.name == INSTANCE_INIT_METHOD_NAME
                        || it.name == STATIC_INIT_METHOD_NAME
                }
                .filterNot {
                    it.name == DEFAULT_ARK_METHOD_NAME
                        && it.signature.enclosingClass.name == DEFAULT_ARK_CLASS_NAME
                }
                .sortedBy { it.signature.toString() }
                .forEach {
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

    fun calculateImprovement(): Double {
        val newExactTypes = exactTypeInferredPreviouslyUnknown +
            exactTypeInferredPreviouslyWasAny +
            exactTypeInferredIncorrectlyPreviouslyKnown +
            arrayInfoPreviouslyUnknown

        val prevTypes = exactTypeInferredCorrectlyPreviouslyKnown +
            typeInfoInferredPreviouslyKnownExactly +
            arrayInfoPreviouslyKnown +
            noInfoInferredPreviouslyKnown

        return newExactTypes.toDouble() / prevTypes * 100
    }

    override fun toString(): String = """
        Total types number: $overallTypes
                
        Compared to the first state of the Scene:
        
        Improvement: ${calculateImprovement()}%
        
        Inferred types that were unknown: $exactTypeInferredPreviouslyUnknown 
        Inferred types that were already inferred: $exactTypeInferredCorrectlyPreviouslyKnown
        Inferred types that were previously inferred as any: $exactTypeInferredPreviouslyWasAny
        Inferred types are different from the ones in the Scene: $exactTypeInferredIncorrectlyPreviouslyKnown

        Array types instead of unknown: $arrayInfoPreviouslyUnknown
        Array types instead of previously known type: $arrayInfoPreviouslyKnown

        Some facts found about unknown type: $typeInfoInferredPreviouslyUnknown 
        Some facts found about already inferred type: $typeInfoInferredPreviouslyKnownExactly

        Unhandled type info: $unhandled

        Lost info about type: $noInfoInferredPreviouslyKnown
        Was known, became undefined: $knownTypeToUndefined
        Nothing inferred, but it was unknown previously as well: $noInfoInferredPreviouslyUnknown 
        
        Was unknown, became undefined: $undefinedUnknownCombination
        Was unknown, became any: $unknownAnyCombination
    """.trimIndent()
}

data class MethodTypesFacts(
    val combinedThisFact: EtsTypeFact?,
    val argumentsFacts: List<EtsTypeFact?>,
    val localFacts: Map<AccessPathBase, EtsTypeFact>,
    val returnFact: EtsTypeFact?,
) {
    companion object {
        fun from(
            result: TypeInferenceResult,
            m: EtsMethod,
        ): MethodTypesFacts {
            val combinedThisFact = result.inferredCombinedThisType.entries.firstOrNull {
                it.key.name == m.signature.enclosingClass.name
            }?.value

            val factsForMethod = result.inferredTypes.entries.singleOrNull {
                compareByMethodNameAndEnclosingClass(it.key, m)
            }?.value

            val inferredReturnType = result.inferredReturnType.entries.firstOrNull {
                compareByMethodNameAndEnclosingClass(it.key, m)
            }?.value

            val arguments = m.parameters.indices.map {
                val stmts = m.cfg.stmts
                if (stmts.isEmpty()) return@map null

                val realIndex = ((stmts[it] as EtsAssignStmt).rhv as EtsParameterRef).index
                factsForMethod?.get(AccessPathBase.Arg(realIndex))
            }

            val locals = factsForMethod?.filterKeys { it is AccessPathBase.Local }.orEmpty()

            return MethodTypesFacts(combinedThisFact, arguments, locals, inferredReturnType)
        }
    }
}

// TODO hack because of an issue with signatures
private val compareByMethodNameAndEnclosingClass = { fst: EtsMethod, snd: EtsMethod ->
    fst.name === snd.name && fst.signature.enclosingClass.name === snd.signature.enclosingClass.name
}

private fun EtsTypeFact.matchesWith(type: EtsType): Boolean {
    val result = when (this) {
        EtsTypeFact.AnyEtsTypeFact -> {
            type is EtsAnyType
        }

        is EtsTypeFact.ObjectEtsTypeFact -> {
            val typeName = this.cls?.typeName

            if (type is EtsUnknownType || type is EtsAnyType) {
                this.cls != null
            } else {
                (type is EtsClassType || type is EtsUnclearRefType) && type.typeName == typeName
            }
        }

        is EtsTypeFact.ArrayEtsTypeFact -> when (type) {
            is EtsArrayType -> this.elementType.matchesWith(type.elementType)

            is EtsUnclearRefType -> {
                val elementType = this.elementType as? EtsTypeFact.ObjectEtsTypeFact
                elementType?.cls?.typeName == type.typeName
            }

            else -> false
        }

        EtsTypeFact.BooleanEtsTypeFact -> {
            type is EtsBooleanType
                || type is EtsUnknownType
                || (type as? EtsClassType)?.typeName == "Boolean"
                || (type as? EtsUnclearRefType)?.typeName == "Boolean"
        }

        EtsTypeFact.FunctionEtsTypeFact -> type is EtsFunctionType || type is EtsUnknownType
        EtsTypeFact.NullEtsTypeFact -> type is EtsNullType || type is EtsUnknownType
        EtsTypeFact.NumberEtsTypeFact -> {
            type is EtsNumberType
                || type is EtsUnknownType
                || (type as? EtsClassType)?.typeName == "Number"
                || (type as? EtsUnclearRefType)?.typeName == "Number"
        }

        EtsTypeFact.StringEtsTypeFact -> {
            type is EtsStringType
                || type is EtsUnknownType
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

        is EtsTypeFact.UnionEtsTypeFact -> types.all { it.matchesWith(type) }
    }

    return result
}

private data class InferenceResult(
    val position: AccessPathBase,
    val type: EtsType,
    val typeFact: EtsTypeFact?,
    val status: InferenceStatus,
)

private enum class InferenceStatus(val message: String) {
    EXACT_MATCH_PREVIOUSLY_KNOWN("Exactly matched, previously known"),
    EXACT_MATCH_PREVIOUSLY_UNKNOWN("Exactly matched, previously unknown"),
    EXACT_MATCH_PREVIOUSLY_WAS_ANY("Exactly matched with any type"),

    TYPE_INFO_FOUND_PREV_KNOWN("Some type facts found, previously known exactly"),
    TYPE_INFO_FOUND_PREV_UNKNOWN("Some type facts found, previously unknown"),
    DIFFERENT_TYPE_FOUND("Another type is inferred"),

    UNHANDLED("Unhandled type info"),

    UNKNOWN_ANY_COMBINATION("Unknown any combination"),
    UNKNOWN_UNDEFINED_COMBINATION("Unknown undefined combination"),
    KNOWN_UNDEFINED_COMBINATION("Known type became undefined"),

    ARRAY_INFO_PREV_UNKNOWN("Found an array type, previously unknown"),

    ARRAY_INFO("Array information"),

    UNION_INSTEAD_OF_UNKNOWN("Discovered union type, previously unknown"),

    NO_INFO_PREVIOUSLY_UNKNOWN("Not inferred, previously unknown"),
    NO_INFO_PREVIOUSLY_KNOWN("Not inferred, previously known")

}
