package org.usvm.fuzzer.types

import org.jacodb.api.*
import org.jacodb.api.ext.constructors

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
            type.typeArguments.map { it.getResolvedTypeWithSubstitutions(substitutions) }
        } else {
            listOf()
        }
    }

    fun getMethodParametersTypes(method: JcTypedMethod) =
        method.parameters.map { jcTypedMethodParameter ->
            jcTypedMethodParameter.type.getResolvedTypeWithSubstitutions(substitutions)
        }

    fun getMethodReturnType(method: JcTypedMethod) =
        method.returnType.getResolvedTypeWithSubstitutions(substitutions)


    fun getFieldType(field: JcTypedField) =
        field.fieldType.getResolvedTypeWithSubstitutions(substitutions)

    fun makeGenericReplacementForSubtype(subtype: JcType) = subtype.getResolvedTypeWithSubstitutions(substitutions)

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