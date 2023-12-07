package org.usvm.fuzzer.types

import org.jacodb.api.*

interface JcGenericGenerator {

    fun replaceGenericParametersForType(type: JcType): JcTypeWrapper

    fun replaceGenericParametersForMethod(resolvedClassType: JcTypeWrapper, method: JcMethod): Pair<JcMethod, List<Substitution>>

}