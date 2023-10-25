package org.usvm.fuzzer.types

import org.jacodb.api.JcClassType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.constructors

class JcTypeImplWrapper(
    val type: JcType,
    val substitutions: List<Substitution>
) {

    fun getDeclaredMethods(): List<JcTypedMethod> = if (type is JcClassType) type.declaredMethods else listOf()

    fun getConstructors(): List<JcTypedMethod> = if (type is JcClassType) type.constructors else listOf()

    fun getMethodParametersTypes(method: JcTypedMethod) =
        method.parameters.map { jcTypedMethodParameter ->
            jcTypedMethodParameter.type.getResolvedTypeWithSubstitutions(substitutions)
        }

    fun getFields(): List<JcTypedField> = if (type is JcClassType) type.fields else listOf()

    fun getFieldReturnType(field: JcTypedField) =
        field.fieldType.getResolvedTypeWithSubstitutions(substitutions)

}