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

import org.jacodb.ets.base.EtsAliasType
import org.jacodb.ets.base.EtsAnnotationNamespaceType
import org.jacodb.ets.base.EtsAnnotationTypeQueryType
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsArrayObjectType
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsGenericType
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsLexicalEnvType
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsObjectLiteral
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.dto.AliasTypeDto
import org.jacodb.ets.dto.AnnotationNamespaceTypeDto
import org.jacodb.ets.dto.AnnotationTypeQueryTypeDto
import org.jacodb.ets.dto.AnyTypeDto
import org.jacodb.ets.dto.ArrayRefDto
import org.jacodb.ets.dto.ArrayTypeDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.FieldSignatureDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.FunctionTypeDto
import org.jacodb.ets.dto.GenericTypeDto
import org.jacodb.ets.dto.InstanceFieldRefDto
import org.jacodb.ets.dto.LexicalEnvTypeDto
import org.jacodb.ets.dto.LiteralTypeDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.LocalSignatureDto
import org.jacodb.ets.dto.MethodParameterDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.NamespaceSignatureDto
import org.jacodb.ets.dto.NeverTypeDto
import org.jacodb.ets.dto.NullTypeDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ParameterRefDto
import org.jacodb.ets.dto.PrimitiveLiteralDto
import org.jacodb.ets.dto.StaticFieldRefDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.ThisRefDto
import org.jacodb.ets.dto.TupleTypeDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.dto.UnclearReferenceTypeDto
import org.jacodb.ets.dto.UndefinedTypeDto
import org.jacodb.ets.dto.UnionTypeDto
import org.jacodb.ets.dto.UnknownTypeDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.dto.VoidTypeDto
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature

fun EtsType.toDto(): TypeDto = accept(EtsTypeToDtoConverter)

object EtsTypeToDtoConverter : EtsType.Visitor<TypeDto> {
    override fun visit(type: EtsAnyType): TypeDto {
        return AnyTypeDto
    }

    override fun visit(type: EtsUnknownType): TypeDto {
        return UnknownTypeDto
    }

    override fun visit(type: EtsUnionType): TypeDto {
        return UnionTypeDto(types = type.types.map { it.accept(this) })
    }

    override fun visit(type: EtsTupleType): TypeDto {
        return TupleTypeDto(types = type.types.map { it.accept(this) })
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

    override fun visit(type: EtsFunctionType): TypeDto {
        return FunctionTypeDto(
            signature = type.method.toDto(),
            typeParameters = type.typeParameters.map { it.toDto() },
        )
    }

    override fun visit(type: EtsArrayType): TypeDto {
        return ArrayTypeDto(
            elementType = type.elementType.toDto(),
            dimensions = type.dimensions,
        )
    }

    override fun visit(type: EtsArrayObjectType): TypeDto {
        TODO("Not yet implemented")
    }

    override fun visit(type: EtsUnclearRefType): TypeDto {
        return UnclearReferenceTypeDto(
            name = type.typeName,
            typeParameters = type.typeParameters.map { it.toDto() },
        )
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

    override fun visit(type: EtsAnnotationNamespaceType): TypeDto {
        return AnnotationNamespaceTypeDto(
            originType = type.originType,
            namespaceSignature = type.namespaceSignature.toDto(),
        )
    }

    override fun visit(type: EtsAnnotationTypeQueryType): TypeDto {
        return AnnotationTypeQueryTypeDto(
            originType = type.originType,
        )
    }

    override fun visit(type: EtsLexicalEnvType): TypeDto {
        return LexicalEnvTypeDto(
            nestedMethod = type.nestedMethod.toDto(),
            closures = type.closures.map { it.toDto() as LocalDto }, // safe cast
        )
    }
}

fun EtsClassSignature.toDto(): ClassSignatureDto =
    ClassSignatureDto(
        name = this.name,
        declaringFile = this.file.toDto(),
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
        declaringFile = this.file.toDto(),
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

fun EtsFieldSignature.toDto(): FieldSignatureDto =
    FieldSignatureDto(
        declaringClass = this.enclosingClass.toDto(),
        name = this.name,
        type = this.type.toDto(),
    )

fun EtsValue.toDto(): ValueDto = accept(EtsValueToDtoConverter)

object EtsValueToDtoConverter : EtsValue.Visitor<ValueDto> {
    override fun visit(value: EtsLocal): ValueDto {
        return LocalDto(
            name = value.name,
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsStringConstant): ValueDto {
        return ConstantDto(
            value = value.value,
            type = StringTypeDto,
        )
    }

    override fun visit(value: EtsBooleanConstant): ValueDto {
        return ConstantDto(
            value = value.value.toString(),
            type = BooleanTypeDto,
        )
    }

    override fun visit(value: EtsNumberConstant): ValueDto {
        return ConstantDto(
            value = value.value.toString(),
            type = NumberTypeDto,
        )
    }

    override fun visit(value: EtsNullConstant): ValueDto {
        return ConstantDto(
            value = "null",
            type = NullTypeDto,
        )
    }

    override fun visit(value: EtsUndefinedConstant): ValueDto {
        return ConstantDto(
            value = "undefined",
            type = UndefinedTypeDto,
        )
    }

    override fun visit(value: EtsArrayLiteral): ValueDto {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsObjectLiteral): ValueDto {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsThis): ValueDto {
        return ThisRefDto(
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsParameterRef): ValueDto {
        return ParameterRefDto(
            index = value.index,
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsArrayAccess): ValueDto {
        return ArrayRefDto(
            array = value.array.toDto(),
            index = value.index.toDto(),
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsInstanceFieldRef): ValueDto {
        return InstanceFieldRefDto(
            instance = value.instance.toDto(),
            field = value.field.toDto(),
        )
    }

    override fun visit(value: EtsStaticFieldRef): ValueDto {
        return StaticFieldRefDto(
            field = value.field.toDto(),
        )
    }
}
