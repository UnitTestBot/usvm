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

package org.usvm.ts.grpc

import org.jacodb.ets.grpc.toProto
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.toType
import usvm.argumentTypeResult
import usvm.classTypeResult
import usvm.fieldTypeResult
import usvm.inferredTypes
import usvm.localTypeResult
import usvm.methodTypeResult
import usvm.InferredTypes as ProtoInferredTypes

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
                    fieldTypeResult {
                        this.name = name
                        this.type = it.toProto()
                    }
                }
            }
            .sortedBy { it.name }
        classTypeResult {
            this.signature = clazz.toProto()
            this.fields += fields
            this.methods += methods
        }
    }.sortedBy {
        it.signature.toString()
    }

    val methodTypeInferenceResult = inferredTypes.map { (method, facts) ->
        val args = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Arg) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull argumentTypeResult {
                        this.index = base.index
                        this.type = type.toProto()
                    }
                }
            }
            null
        }.sortedBy { it.index }
        val returnType = inferredReturnType[method]?.toType()?.toProto()
        val locals = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Local) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull localTypeResult {
                        this.name = base.name
                        this.type = type.toProto()
                    }
                }
            }
            null
        }.sortedBy { it.name }
        methodTypeResult {
            this.signature = method.signature.toProto()
            this.args += args
            returnType?.let { this.returnType = it }
            this.locals += locals
        }
    }.sortedBy {
        it.signature.toString()
    }

    return inferredTypes {
        this.classes += classTypeInferenceResult
        this.methods += methodTypeInferenceResult
    }
}
