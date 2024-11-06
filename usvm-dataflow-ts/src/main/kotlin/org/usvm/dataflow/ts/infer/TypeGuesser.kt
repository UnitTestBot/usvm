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

package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod

fun guessTypes(
    graph: EtsApplicationGraph,
    facts: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    allowResolvedAlternatives: Boolean,
): Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>> {
    return facts.mapValues { (_, types) ->
        if (types.isNotEmpty() && types.entries.singleOrNull()?.value != EtsTypeFact.UnknownEtsTypeFact) {
            val updatedTypes = types.mapValues { (_, fact) ->
                fact.resolveType(graph, allowResolvedAlternatives)
            }
            return@mapValues updatedTypes
        }
        types
    }
}

fun EtsTypeFact.resolveType(
    graph: EtsApplicationGraph,
    allowResolvedAlternatives: Boolean = false,
    filterAnonymous: Boolean = false
): EtsTypeFact {
    return when (this) {
        is EtsTypeFact.ArrayEtsTypeFact -> {
            val elementType = this.elementType
            if (elementType is EtsTypeFact.UnknownEtsTypeFact) {
                this
            } else {
                this.copy(elementType = elementType.resolveType(graph, allowResolvedAlternatives))
            }
        }

        is EtsTypeFact.ObjectEtsTypeFact -> {
            if (cls != null) {
                return this
            }

            val touchedPropertiesNames = this.properties.keys
            val classesInSystem = graph.cp
                .classes
                .filter { cls ->
                    val methodNames = cls.methods.map { it.name }
                    val fieldNames = cls.fields.map { it.name }
                    val propertiesNames = (methodNames + fieldNames).distinct()
                    touchedPropertiesNames.all { name -> name in propertiesNames }
                }

            if (classesInSystem.isEmpty()) {
                val indicesProperties = this.properties.filter { (k, _) -> k.toIntOrNull() != null }
                if (indicesProperties.isNotEmpty()) {
                    val elementTypeFacts = indicesProperties.map {
                        it.value.resolveType(graph, allowResolvedAlternatives, filterAnonymous).simplify()
                    }.toSet()
                    val typeFact = EtsTypeFact.mkUnionType(elementTypeFacts)
                    return EtsTypeFact.ArrayEtsTypeFact(typeFact)
                }

                if ("length" in touchedPropertiesNames && "splice" in touchedPropertiesNames) {
                    return EtsTypeFact.ArrayEtsTypeFact(EtsTypeFact.AnyEtsTypeFact)
                }

                return this
            }

            val suitableTypes = classesInSystem
                .filter { !filterAnonymous || !it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
                .map {
                    // TODO make it an impossible unique prefix
                    // TODO how to do it properly?
                    EtsTypeFact.ObjectEtsTypeFact(
                        cls = EtsClassType(
                            EtsClassSignature(
                                name = it.name,
                                file = EtsFileSignature.DEFAULT,
                            )
                        ),
                        // properties = emptyMap(),
                        // TODO it is correct? Mb we should save the properties?
                        properties = properties.mapValues {
                            it.value.resolveType(graph, allowResolvedAlternatives, filterAnonymous)
                        }
                    )
                }.toSet()

            val notAnonymousSuitableTypes = suitableTypes.filterNot {
                it.cls?.typeName?.startsWith(ANONYMOUS_CLASS_PREFIX) ?: error("Should not occur")
            }.toSet()

            // TODO process arrays here (and strings)

            when {
                suitableTypes.isEmpty() && notAnonymousSuitableTypes.isEmpty() -> {
                    error("Should be processed earlier")
                }
                notAnonymousSuitableTypes.isEmpty() -> {
                    EtsTypeFact.mkUnionType(suitableTypes)
                }
                notAnonymousSuitableTypes.size == 1 -> notAnonymousSuitableTypes.single()
                notAnonymousSuitableTypes.size in 2..5 && allowResolvedAlternatives ->{
                    EtsTypeFact.mkUnionType(notAnonymousSuitableTypes)
                }
                else -> this
            }
        }

        is EtsTypeFact.FunctionEtsTypeFact -> this
        is EtsTypeFact.GuardedTypeFact -> TODO("guarded")
        is EtsTypeFact.IntersectionEtsTypeFact -> {
            val updatedTypes = types.mapTo(hashSetOf()) { it.resolveType(graph, allowResolvedAlternatives) }
            EtsTypeFact.IntersectionEtsTypeFact(updatedTypes)
        }

        is EtsTypeFact.UnionEtsTypeFact -> {
            this.copy(
                types.mapTo(mutableSetOf()) { it.resolveType(graph, allowResolvedAlternatives) }
                    .filterNot { it is EtsTypeFact.AnyEtsTypeFact }
                    .toSet()
            ).simplify()
        }

        else -> this
    }.simplify()
}

fun EtsTypeFact.simplify(): EtsTypeFact = when (this) {
    is EtsTypeFact.UnionEtsTypeFact -> {
        val args = this.types.map { it.simplify() }

        require(args.isNotEmpty())

        val types = args.foldRight(mutableSetOf<EtsTypeFact>()) { current, acc ->
            if (current is EtsTypeFact.ObjectEtsTypeFact && current.cls == null && current.properties.isEmpty()) {
                acc
            } else {
                acc.add(current)
                acc
            }
        }

        if (types.size == 1) types.single() else EtsTypeFact.UnionEtsTypeFact(types)
    }

    is EtsTypeFact.IntersectionEtsTypeFact -> {
        val args = this.types.map { it.simplify() }
        val splittedArgs = args.partition { it is EtsTypeFact.ObjectEtsTypeFact && it.cls == null }
        val newArgs = splittedArgs.second + splittedArgs.first.let {
            val allProperties = it
                .flatMap { (it as EtsTypeFact.ObjectEtsTypeFact).properties.entries }
                .map { it.key to it.value }
            EtsTypeFact.ObjectEtsTypeFact(cls = null, properties = allProperties.toMap())
        }

        if (newArgs.size == 1) newArgs.single() else EtsTypeFact.IntersectionEtsTypeFact(newArgs.toHashSet())
    }

    is EtsTypeFact.GuardedTypeFact -> {
        TODO()
    }

    is EtsTypeFact.ArrayEtsTypeFact -> {
        copy(elementType.simplify())
    }

    is EtsTypeFact.ObjectEtsTypeFact -> {
        val props = properties.mapValues { it.value.simplify() }
        copy(properties = props)
    }

    else -> this
}
