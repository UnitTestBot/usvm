/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsType
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.toType

class InferredMethodTypeScheme(
    method: EtsMethod,
    typeInferenceResult: TypeInferenceResult,
) : MethodTypeScheme {
    val types = typeInferenceResult.inferredTypes[method].orEmpty()

    override fun typeOf(base: AccessPathBase): EtsType? = types[base]?.toType()
}

class InferredTypeScheme(
    private val typeInferenceResult: TypeInferenceResult,
) : TypeScheme {
    override fun methodScheme(method: EtsMethod): MethodTypeScheme =
        InferredMethodTypeScheme(method, typeInferenceResult)

    override fun methodSchemes(): List<MethodTypeScheme> =
        methods.map { methodScheme(it) }

    private val methods by lazy {
        with(typeInferenceResult) {
            inferredTypes.keys + inferredReturnType.keys
        }
    }

    override fun thisType(method: EtsMethod): EtsType? =
        typeInferenceResult.inferredCombinedThisType[method.signature.enclosingClass]?.toType()
}

class MethodTypeSchemeImpl(
    val types: Map<AccessPathBase, EtsType>,
) : MethodTypeScheme {
    override fun typeOf(base: AccessPathBase): EtsType? = types[base]
}

class TypeSchemeImpl(
    private val methodTypeSchemes: Map<EtsMethod, MethodTypeSchemeImpl>,
) : TypeScheme {
    override fun thisType(method: EtsMethod): EtsType? =
        methodTypeSchemes[method]?.typeOf(AccessPathBase.This)

    override fun methodScheme(method: EtsMethod): MethodTypeScheme =
        methodTypeSchemes[method] ?: MethodTypeSchemeImpl(emptyMap())

    override fun methodSchemes(): List<MethodTypeScheme> = methodTypeSchemes.values.toList()
}
