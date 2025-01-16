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

package org.usvm.dataflow.ts.infer.dto

import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.FieldSignatureDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.MethodParameterDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NamespaceSignatureDto
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.toType

fun TypeInferenceResult.toDto(): InferredTypesDto {
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
                    FieldTypeResultDto(name, it.toDto())
                }
            }
            .sortedBy { it.name }
        ClassTypeResultDto(clazz.toDto(), fields, methods)
    }.sortedBy {
        it.signature.toString()
    }

    val methodTypeInferenceResult = inferredTypes.map { (method, facts) ->
        val args = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Arg) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull ArgumentTypeResultDto(base.index, type.toDto())
                }
            }
            null
        }.sortedBy { it.index }
        val returnType = inferredReturnType[method]?.toType()?.toDto()
        val locals = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Local) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull LocalTypeResultDto(base.name, type.toDto())
                }
            }
            null
        }.sortedBy { it.name }
        MethodTypeResultDto(method.signature.toDto(), args, returnType, locals)
    }.sortedBy {
        it.signature.toString()
    }

    return InferredTypesDto(classTypeInferenceResult, methodTypeInferenceResult)
}

fun EtsFileSignature.toDto(): FileSignatureDto =
    FileSignatureDto(
        projectName = this.projectName,
        fileName = this.fileName,
    )

fun EtsNamespaceSignature.toDto(): NamespaceSignatureDto =
    NamespaceSignatureDto(
        name = this.name,
        declaringFile = this.file.toDto(),
        declaringNamespace = this.namespace?.toDto(),
    )

fun EtsClassSignature.toDto(): ClassSignatureDto =
    ClassSignatureDto(
        name = this.name,
        declaringFile = this.file.toDto(),
        declaringNamespace = this.namespace?.toDto(),
    )

fun EtsFieldSignature.toDto(): FieldSignatureDto =
    FieldSignatureDto(
        declaringClass = this.enclosingClass.toDto(),
        name = this.name,
        type = this.type.toDto(),
    )

fun EtsMethodSignature.toDto(): MethodSignatureDto =
    MethodSignatureDto(
        declaringClass = this.enclosingClass.toDto(),
        name = this.name,
        parameters = this.parameters.map { it.toDto() },
        returnType = this.returnType.toDto(),
    )

fun EtsMethodParameter.toDto(): MethodParameterDto =
    MethodParameterDto(
        name = this.name,
        type = this.type.toDto(),
        isOptional = this.isOptional,
    )
