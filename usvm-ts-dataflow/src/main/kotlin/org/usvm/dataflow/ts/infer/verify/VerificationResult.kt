/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ts.infer.verify

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.dataflow.ts.infer.annotation.MethodTypeSchemeImpl
import org.usvm.dataflow.ts.infer.annotation.TypeScheme
import org.usvm.dataflow.ts.infer.annotation.TypeSchemeImpl
import org.usvm.dataflow.ts.infer.verify.collectors.MethodVerificationSummary

sealed interface VerificationResult {
    data class Success(val scheme: TypeScheme) : VerificationResult

    data class Fail(val summary: Map<EtsMethod, MethodVerificationSummary>) : VerificationResult {
        val erasureScheme by lazy {
            TypeSchemeImpl(
                summary.mapValues { (_, summary) ->
                    MethodTypeSchemeImpl(
                        summary.entitySummaries
                            .filterValues { it.errors.isNotEmpty() || it.types.size != 1 }
                            .mapValues { _ -> EtsUnknownType }
                    )
                }
            )
        }
    }

    companion object {
        fun from(summary: Map<EtsMethod, MethodVerificationSummary>): VerificationResult {
            val summaryIsValid = summary.values.all { methodSummary ->
                methodSummary.entitySummaries.values.all { it.types.size == 1 && it.errors.isEmpty() }
            }

            return if (summaryIsValid) {
                val methodTypeSchemes = summary.mapValues { (_, summary) ->
                    val types = summary.entitySummaries.mapValues { (_, entitySummary) ->
                        entitySummary.types.single()
                    }
                    MethodTypeSchemeImpl(types)
                }

                Success(TypeSchemeImpl(methodTypeSchemes))
            } else {
                Fail(summary)
            }
        }
    }
}
