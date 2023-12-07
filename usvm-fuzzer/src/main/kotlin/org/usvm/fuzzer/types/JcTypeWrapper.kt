@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.usvm.fuzzer.types

import org.jacodb.api.*
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.toType
import org.jacodb.impl.types.typeParameters
import org.usvm.instrumentation.util.toJcClass

class JcTypeWrapper(
    val type: JcType,
    val substitutions: List<Substitution>
) {

    val constructors: List<JcTypedMethod> by lazy {
        if (type is JcClassType) {
            type.constructors
        } else {
            listOf()
        }
    }

    val declaredMethods: List<JcTypedMethod> by lazy {
        if (type is JcClassType) {
            type.declaredMethods
        } else {
            listOf()
        }
    }

    val declaredFields: List<JcTypedField> by lazy {
        if (type is JcClassType) {
            type.declaredFields
        } else {
            listOf()
        }
    }

    val typeArguments: List<JcTypeWrapper> by lazy {
        if (type is JcClassType) {
            type.typeArguments.map { JcTypeWrapper(it, listOf()) }
        } else {
            listOf()
        }
    }

    fun getMethodParametersTypes(
        method: JcTypedMethod,
        methodSubstitutions: List<Substitution>
    ): List<JcTypeWrapper> =
        method.parameters.map { jcTypedMethodParameter ->
            if (jcTypedMethodParameter.type.toJcClass()?.typeParameters?.isNotEmpty() == true) {
                resolveJcType(jcTypedMethodParameter.type, methodSubstitutions)
            } else {
                JcTypeWrapper(jcTypedMethodParameter.type, listOf())
            }
        }

    fun resolveJcType(jcType: JcType): JcTypeWrapper =
        resolveJcType(jcType, listOf())
    private fun resolveJcType(jcType: JcType, additionalSubstitutions: List<Substitution>): JcTypeWrapper =
        when (jcType) {
            is JcClassType -> resolveClassType(jcType, substitutions + additionalSubstitutions)
            is JcTypeVariable -> (substitutions + additionalSubstitutions).find { it.typeParam.symbol == jcType.symbol }?.substitution
                ?: error("Cant find sub for $jcType")

            is JcArrayType -> {
                if (jcType.elementType is JcClassType) {
                    val substitutions =
                        resolveClassType(
                            type = jcType.elementType as JcClassType,
                            substitutions = substitutions + additionalSubstitutions
                        ).substitutions
                    JcTypeWrapper(jcType, substitutions)
                } else {
                    JcTypeWrapper(jcType, listOf())
                }
            }

            else -> JcTypeWrapper(jcType, listOf())
        }

    private fun resolveClassType(type: JcClassType, substitutions: List<Substitution>): JcTypeWrapper = with(type) {
        if (typeParameters.isEmpty()) return JcTypeWrapper(type, listOf())
        val s =
            typeArguments.zip(typeParameters).map { (typeArg, typeParam) ->
                val substitution =
                    when (typeArg) {
                        is JcClassType -> resolveClassType(typeArg, substitutions)
                        is JcTypeVariable -> substitutions.find { it.typeParam.symbol == typeArg.symbol }?.substitution
                            ?: error("Can't find substitution for ${typeArg.typeName}")
                        is JcBoundedWildcard -> {
                            //TODO make in work for multiple bounds
                            when (val upperBound = typeArg.upperBounds.first()) {
                                is JcTypeVariable -> substitutions.find { it.typeParam.symbol == upperBound.symbol }?.substitution!!
                                is JcClassType -> resolveClassType(upperBound, substitutions)
                                else -> error("Not expected bound")
                            }
                        }
                        else -> error("Unexpected type arg")
                    }
                Substitution(typeParam, substitution)
            }
        return JcTypeWrapper(type, s)
    }

    fun getMethodReturnType(method: JcTypedMethod): JcTypeWrapper {
        val methodSubstitutions =
            if (method.typeParameters.isNotEmpty()) {
                JcGenericGeneratorImpl(type.classpath).replaceGenericParametersForMethod(this, method.method).second
            } else {
                emptyList()
            }
        return resolveJcType(method.returnType, methodSubstitutions)
    }


    fun getFieldType(field: JcTypedField): JcTypeWrapper = resolveJcType(field.fieldType, listOf())

//    fun makeGenericReplacementForSubtype(subtype: JcType) = subtype.getResolvedTypeWithSubstitutions(substitutions)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcTypeWrapper

        if (type != other.type) return false
        if (substitutions != other.substitutions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + substitutions.hashCode()
        return result
    }

}