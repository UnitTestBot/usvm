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

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayObjectType
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.dto.AnyTypeDto
import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.FunctionTypeDto
import org.jacodb.ets.dto.LiteralTypeDto
import org.jacodb.ets.dto.MethodParameterDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NamespaceSignatureDto
import org.jacodb.ets.dto.NeverTypeDto
import org.jacodb.ets.dto.NullTypeDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.TupleTypeDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.dto.UnclearReferenceTypeDto
import org.jacodb.ets.dto.UndefinedTypeDto
import org.jacodb.ets.dto.UnionTypeDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.VoidTypeDto
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact

fun AccessPathBase.toDto(): AccessPathBaseDto = when (this) {
    AccessPathBase.This -> AccessPathBaseDto.This
    AccessPathBase.Static -> AccessPathBaseDto.Static
    is AccessPathBase.Arg -> AccessPathBaseDto.Arg(this.index)
    is AccessPathBase.Local -> AccessPathBaseDto.Local(this.name)
    is AccessPathBase.Const -> AccessPathBaseDto.Const(this.constant.toString())
    else -> error("Cannot convert to DTO: $this")
}

fun EtsTypeFact.toDto(): TypeFactDto = when (this) {
    EtsTypeFact.AnyEtsTypeFact -> TypeFactDto.AnyTypeFact
    EtsTypeFact.UnknownEtsTypeFact -> TypeFactDto.UnknownTypeFact
    EtsTypeFact.NullEtsTypeFact -> TypeFactDto.NullTypeFact
    EtsTypeFact.UndefinedEtsTypeFact -> TypeFactDto.UndefinedTypeFact
    EtsTypeFact.BooleanEtsTypeFact -> TypeFactDto.BooleanTypeFact
    EtsTypeFact.NumberEtsTypeFact -> TypeFactDto.NumberTypeFact
    EtsTypeFact.StringEtsTypeFact -> TypeFactDto.StringTypeFact
    EtsTypeFact.FunctionEtsTypeFact -> TypeFactDto.FunctionTypeFact

    is EtsTypeFact.ArrayEtsTypeFact -> TypeFactDto.ArrayTypeFact(
        elementType = this.elementType.toDto()
    )

    is EtsTypeFact.ObjectEtsTypeFact -> TypeFactDto.ObjectTypeFact(
        cls = this.cls?.toDto(),
        properties = this.properties.mapValues { it.value.toDto() }
    )

    is EtsTypeFact.IntersectionEtsTypeFact -> TypeFactDto.IntersectionTypeFact(
        types = this.types.map { it.toDto() }
    )

    is EtsTypeFact.UnionEtsTypeFact -> TypeFactDto.UnionTypeFact(
        types = this.types.map { it.toDto() }
    )

    else -> error("Cannot convert to DTO: $this")
}

fun EtsType.toDto(): TypeDto = when (this) {
    is EtsAnyType -> AnyTypeDto
    is EtsUnknownType -> UnknownTypeDto
    is EtsUnionType -> UnionTypeDto(types = this.types.map { it.toDto() })
    is EtsTupleType -> TupleTypeDto(types = this.types.map { it.toDto() })
    is EtsBooleanType -> BooleanTypeDto
    is EtsNumberType -> NumberTypeDto
    is EtsStringType -> StringTypeDto
    is EtsNullType -> NullTypeDto
    is EtsUndefinedType -> UndefinedTypeDto
    is EtsVoidType -> VoidTypeDto
    is EtsNeverType -> NeverTypeDto
    is EtsLiteralType -> LiteralTypeDto(literal = this.literalTypeName)
    is EtsClassType -> ClassTypeDto(signature = this.classSignature.toDto())
    is EtsFunctionType -> FunctionTypeDto(signature = this.method.toDto())
    is EtsArrayType -> ArrayTypeDto(elementType = this.elementType.toDto(), dimensions = this.dimensions)
    is EtsArrayObjectType -> TODO("removed")
    is EtsUnclearRefType -> UnclearReferenceTypeDto(name = this.typeName)
    else -> error("Cannot convert to DTO: $this")
}

fun EtsClassSignature.toDto(): ClassSignatureDto =
    ClassSignatureDto(
        name = this.name,
        declaringFile = this.file?.toDto(),
        declaringNamespace = this.namespace?.toDto(),
    )

fun EtsFileSignature.toDto(): FileSignatureDto =
    FileSignatureDto(
        projectName = this.projectName,
        fileName = this.fileName,
    )

fun EtsNamespaceSignature.toDto(): NamespaceSignatureDto =
    NamespaceSignatureDto(
        name = this.name,
        declaringFile = this.file?.toDto(),
        declaringNamespace = this.namespace?.toDto(),
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
