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

import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod

fun guessUniqueTypes(
    graph: EtsApplicationGraph,
    facts: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
): Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>> {
    return facts.mapValues { (_, types) ->
        if (types.isNotEmpty() && types.entries.singleOrNull()?.value != EtsTypeFact.UnknownEtsTypeFact) {
            val updatedTypes = types.mapValues { (_, fact) ->
                fact.resolveType(graph)
            }
            return@mapValues updatedTypes
        }
        types
    }
}

private fun EtsTypeFact.resolveType(graph: EtsApplicationGraph): EtsTypeFact = when (this) {
    is EtsTypeFact.ArrayEtsTypeFact -> {
        val elementType = this.elementType
        if (elementType is EtsTypeFact.UnknownEtsTypeFact) {
            this
        } else {
            this.copy(elementType = elementType.resolveType(graph))
        }
    }

    is EtsTypeFact.ObjectEtsTypeFact -> {
        val touchedPropertiesNames = this.properties.keys
        val classesInSystem = graph.cp
            .classes
            .filter { cls ->
                val methodNames = cls.methods.map { it.name }
                val fieldNames = cls.fields.map { it.name }
                val propertiesNames = (methodNames + fieldNames).distinct()
                touchedPropertiesNames.all { name -> name in propertiesNames }
            }

        classesInSystem.singleOrNull()
            // TODO make it an impossible unique prefix
            ?.takeUnless { it.name.startsWith("AnonymousClass-") }
            ?.let {
                println("UPDATED TYPE FOR ${it.name}")
                // TODO how to do it properly?
                EtsTypeFact.ObjectEtsTypeFact(
                    cls = EtsClassType(EtsClassSignature(it.name)),
                    properties = emptyMap(),
                    // TODO it is correct? Mb we should save the properties?
                    // properties = properties.mapValues { it.value.resolveType() }
                )
            } ?: this
    }

    is EtsTypeFact.FunctionEtsTypeFact -> this
    is EtsTypeFact.GuardedTypeFact -> TODO("guarded")
    is EtsTypeFact.IntersectionEtsTypeFact -> {
        val updatedTypes = types.mapTo(hashSetOf()) { it.resolveType(graph) }
        EtsTypeFact.IntersectionEtsTypeFact(updatedTypes)
    }

    is EtsTypeFact.UnionEtsTypeFact -> {
        this.copy(types.mapTo(mutableSetOf()) { it.resolveType(graph) })
    }

    else -> this
}
