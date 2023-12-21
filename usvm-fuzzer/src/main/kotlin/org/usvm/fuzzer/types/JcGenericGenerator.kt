package org.usvm.fuzzer.types

import java.lang.reflect.*

interface JcGenericGenerator {

    fun resolveGenericParametersForType(type: JcTypeWrapper): JcTypeWrapper

    // Pair<JcTypeWrapper, List<JcTypeWrapper>> -> return type (for method) to parameter types
    fun resolveGenericParametersForMethod(resolvedClassType: JcTypeWrapper, method: Method): Pair<JcTypeWrapper, List<JcTypeWrapper>>

    fun resolveMethodReturnType(resolvedClassType: JcTypeWrapper, method: Method): JcTypeWrapper

    // List<JcTypeWrapper> -> parameter types
    fun resolveGenericParametersForConstructor(resolvedClassType: JcTypeWrapper, constructor: Constructor<*>): List<JcTypeWrapper>

    fun getFieldType(resolvedClassType: JcTypeWrapper, field: Field): JcTypeWrapper

}