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

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod

data class TypeInferenceResult(
    val inferredTypes: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    val inferredReturnType: Map<EtsMethod, EtsTypeFact>,
    val inferredCombinedThisType: Map<EtsClassSignature, EtsTypeFact>,
) {
    fun withGuessedTypes(guesser: TypeGuesser): TypeInferenceResult =
        TypeInferenceResult(
            inferredTypes = inferredTypes.mapValues { (_, facts) ->
                facts.mapValues { (_, fact) ->
                    guesser.guess(fact)
                }
            },
            inferredReturnType = inferredReturnType.mapValues { (_, fact) ->
                guesser.guess(fact)
            },
            inferredCombinedThisType = inferredCombinedThisType.mapValues { (_, fact) ->
                guesser.guess(fact)
            },
        )
}
