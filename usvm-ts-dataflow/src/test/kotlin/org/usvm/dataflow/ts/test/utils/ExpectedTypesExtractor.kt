package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

class ExpectedTypesExtractor(
    private val scene: EtsScene,
) {
    fun extractTypes(method: EtsMethod): MethodTypes {
        val returnType = method.returnType
        val argumentsTypes = method.parameters.map { it.type }
        val thisType = scene.getEtsClassType(method.signature.enclosingClass)
        return MethodTypes(thisType, argumentsTypes, returnType)
    }
}
