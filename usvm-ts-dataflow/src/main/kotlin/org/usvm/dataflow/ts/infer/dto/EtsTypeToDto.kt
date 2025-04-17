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

package org.usvm.dataflow.ts.infer.dto

import org.jacodb.ets.dto.AliasTypeDto
import org.jacodb.ets.dto.AnyTypeDto
import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.FunctionTypeDto
import org.jacodb.ets.dto.GenericTypeDto
import org.jacodb.ets.dto.IntersectionTypeDto
import org.jacodb.ets.dto.LiteralTypeDto
import org.jacodb.ets.dto.LocalSignatureDto
import org.jacodb.ets.dto.NeverTypeDto
import org.jacodb.ets.dto.NullTypeDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.PrimitiveLiteralDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.TupleTypeDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.dto.UnclearReferenceTypeDto
import org.jacodb.ets.dto.UndefinedTypeDto
import org.jacodb.ets.dto.UnionTypeDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.VoidTypeDto
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsVoidType

fun EtsType.toDto(): TypeDto = accept(EtsTypeToDto)

private object EtsTypeToDto : EtsType.Visitor<TypeDto> {
    override fun visit(type: EtsAnyType): TypeDto {
        return AnyTypeDto
    }

    override fun visit(type: EtsUnknownType): TypeDto {
        return UnknownTypeDto
    }

    override fun visit(type: EtsUnionType): TypeDto {
        return UnionTypeDto(types = type.types.map { it.accept(this) })
    }

    override fun visit(type: EtsIntersectionType): TypeDto {
        return IntersectionTypeDto(types = type.types.map { it.accept(this) })
    }

    override fun visit(type: EtsGenericType): TypeDto {
        return GenericTypeDto(
            name = type.typeName,
            defaultType = type.defaultType?.toDto(),
            constraint = type.constraint?.toDto(),
        )
    }

    override fun visit(type: EtsAliasType): TypeDto {
        return AliasTypeDto(
            name = type.name,
            originalType = type.originalType.toDto(),
            signature = LocalSignatureDto(
                type.signature.name,
                type.signature.method.toDto(),
            ),
        )
    }

    override fun visit(type: EtsBooleanType): TypeDto {
        return BooleanTypeDto
    }

    override fun visit(type: EtsNumberType): TypeDto {
        return NumberTypeDto
    }

    override fun visit(type: EtsStringType): TypeDto {
        return StringTypeDto
    }

    override fun visit(type: EtsNullType): TypeDto {
        return NullTypeDto
    }

    override fun visit(type: EtsUndefinedType): TypeDto {
        return UndefinedTypeDto
    }

    override fun visit(type: EtsVoidType): TypeDto {
        return VoidTypeDto
    }

    override fun visit(type: EtsNeverType): TypeDto {
        return NeverTypeDto
    }

    override fun visit(type: EtsLiteralType): TypeDto {
        val literal = when {
            type.literalTypeName.equals("true", ignoreCase = true) -> {
                PrimitiveLiteralDto.BooleanLiteral(true)
            }

            type.literalTypeName.equals("false", ignoreCase = true) -> {
                PrimitiveLiteralDto.BooleanLiteral(false)
            }

            else -> {
                val x = type.literalTypeName.toDoubleOrNull()
                if (x != null) {
                    PrimitiveLiteralDto.NumberLiteral(x)
                } else {
                    PrimitiveLiteralDto.StringLiteral(type.literalTypeName)
                }
            }
        }
        return LiteralTypeDto(literal = literal)
    }

    override fun visit(type: EtsClassType): TypeDto {
        return ClassTypeDto(
            signature = type.signature.toDto(),
            typeParameters = type.typeParameters.map { it.toDto() },
        )
    }

    override fun visit(type: EtsUnclearRefType): TypeDto {
        return UnclearReferenceTypeDto(
            name = type.typeName,
            typeParameters = type.typeParameters.map { it.toDto() },
        )
    }

    override fun visit(type: EtsArrayType): TypeDto {
        return ArrayTypeDto(
            elementType = type.elementType.toDto(),
            dimensions = type.dimensions,
        )
    }

    override fun visit(type: EtsTupleType): TypeDto {
        return TupleTypeDto(types = type.types.map { it.accept(this) })
    }

    override fun visit(type: EtsFunctionType): TypeDto {
        return FunctionTypeDto(
            signature = type.signature.toDto(),
            typeParameters = type.typeParameters.map { it.toDto() },
        )
    }
}
