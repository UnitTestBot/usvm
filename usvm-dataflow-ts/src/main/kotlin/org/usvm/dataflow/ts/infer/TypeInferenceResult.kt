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

import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod

data class TypeInferenceResult(
    val inferredTypes: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    val inferredReturnType: Map<EtsMethod, EtsTypeFact>,
    val inferredCombinedThisType: Map<EtsClassSignature, EtsTypeFact>,
) {
    fun withGuessedTypes(graph: EtsApplicationGraph): TypeInferenceResult {
        return TypeInferenceResult(
            inferredTypes = guessUniqueTypes(graph, inferredTypes),
            inferredReturnType = inferredReturnType.mapValues { (_, fact) -> fact.resolveType(graph) },
            inferredCombinedThisType = inferredCombinedThisType.mapValues { (_, fact) -> fact.resolveType(graph) },
        )
    }

    // TODO combination should be made on another level, combining facts
    fun merge(other: TypeInferenceResult) : TypeInferenceResult {
        val inferredTypes = inferredTypes.toMutableMap()
        other.inferredTypes.forEach {
            if (inferredTypes.containsKey(it.key)) {
                val values = inferredTypes.getValue(it.key).toMutableMap()

                it.value.forEach {
                    if (values.containsKey(it.key)) {
                        TODO()
                    } else {
                        values[it.key] = it.value
                    }
                }

                inferredTypes[it.key] = values
            } else {
                inferredTypes[it.key] = it.value
            }
        }

        val inferredReturnType = inferredReturnType.toMutableMap()
        other.inferredReturnType.forEach {
            if (inferredReturnType.containsKey(it.key)) {
                TODO()
            } else {
                inferredReturnType[it.key] = it.value
            }
        }

        val inferredCombinedThisType = inferredCombinedThisType.toMutableMap()
        other.inferredCombinedThisType.forEach {
            if (inferredCombinedThisType.containsKey(it.key)) {
                TODO()
            } else {
                inferredCombinedThisType[it.key] = it.value
            }
        }

        return TypeInferenceResult(inferredTypes, inferredReturnType, inferredCombinedThisType)
    }
}
