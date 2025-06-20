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

package org.usvm.ts.service

import org.jacodb.ets.proto.toProto
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.toType
import usvm.ArgumentTypeResult as ProtoArgumentTypeResult
import usvm.ClassTypeResult as ProtoClassTypeResult
import usvm.FieldTypeResult as ProtoFieldTypeResult
import usvm.InferredTypes as ProtoInferredTypes
import usvm.LocalTypeResult as ProtoLocalTypeResult
import usvm.MethodTypeResult as ProtoMethodTypeResult

fun TypeInferenceResult.toProto(): ProtoInferredTypes {
    val classTypeInferenceResult = inferredCombinedThisType.map { (clazz, fact) ->
        val properties = (fact as? EtsTypeFact.ObjectEtsTypeFact)?.properties ?: emptyMap()
        val methods = properties
            .filter { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .keys
            .sortedBy { it }
        val fields = properties
            .filterNot { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .mapNotNull { (name, fact) ->
                fact.toType()?.let {
                    ProtoFieldTypeResult(
                        name = name,
                        type = it.toProto(),
                    )
                }
            }
            .sortedBy { it.name }
        ProtoClassTypeResult(
            signature = clazz.toProto(),
            fields = fields,
            methods = methods,
        )
    }.sortedBy {
        it.signature.toString()
    }

    val methodTypeInferenceResult = inferredTypes.map { (method, facts) ->
        val args = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Arg) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull ProtoArgumentTypeResult(
                        index = base.index,
                        type = type.toProto(),
                    )
                }
            }
            null
        }.sortedBy { it.index }
        val returnType = inferredReturnType[method]?.toType()?.toProto()
        val locals = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Local) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull ProtoLocalTypeResult(
                        name = base.name,
                        type = type.toProto(),
                    )
                }
            }
            null
        }.sortedBy { it.name }
        ProtoMethodTypeResult(
            signature = method.signature.toProto(),
            args = args,
            returnType = returnType,
            locals = locals,
        )
    }.sortedBy {
        it.signature.toString()
    }

    return ProtoInferredTypes(
        classes = classTypeInferenceResult,
        methods = methodTypeInferenceResult,
    )
}
