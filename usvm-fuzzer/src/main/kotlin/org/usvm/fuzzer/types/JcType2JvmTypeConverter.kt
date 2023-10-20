@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.usvm.fuzzer.types

import kotlinx.collections.immutable.toPersistentMap
import org.jacodb.api.*
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.signature.*
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.toJvmType
import org.usvm.instrumentation.util.zipToMap


fun JcType.convertToJvmType(): JvmType =
    when (this) {
        is JcClassType -> JvmClassRefType(typeName, nullable, annotations)
        is JcArrayType -> JvmArrayType(elementType.convertToJvmType(), nullable, annotations)
        is JcPrimitiveType -> JvmPrimitiveType(typeName, annotations)
        is JcTypeVariable -> JvmTypeVariable(symbol, nullable, annotations)
        is JcUnboundWildcard -> JvmUnboundWildcard
        else -> error("cant generate jvm type for $typeName")
    }

fun JcTypeVariableDeclaration.convertToJvmTypeParameterDeclarationImpl(): JvmTypeParameterDeclaration =
    JvmTypeParameterDeclarationImpl(
        symbol,
        owner,
        bounds.map { it.convertToJvmType() }
    )

fun JcClassTypeImpl.getResolvedType(generics: List<JcType>) =
    JcClassTypeImpl(
        classpath = classpath,
        name = name,
        outerType = outerType,
        substitutor = JcSubstitutorImpl(
            typeParameters
                .map { it.convertToJvmTypeParameterDeclarationImpl() }
                .zipToMap(generics.map { it.convertToJvmType() }).toPersistentMap()
        ),
        nullable = nullable,
        annotations = annotations
    )
