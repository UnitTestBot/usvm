package org.usvm.machine.types

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsBigIntType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsType
import org.usvm.util.humanReadableSignature

/** Bounds only input types whose initial-state construction has no implemented domain. */
internal class TsInitialStateInputGuard {
    fun unsupportedReason(method: EtsMethod): String? {
        val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
        for (parameter in method.parameters) {
            val inputType = parameter.type.initialInputType(genericParameters)
            val domain = inputType.unsupportedInitialDomain() ?: continue

            return "$domain input parameter '${parameter.name}' in ${method.humanReadableSignature} is not modeled"
        }

        return null
    }
}

private fun EtsType.unsupportedInitialDomain(): String? = when (this) {
    is EtsFunctionType -> "Callable"
    EtsBigIntType -> "BigInt"
    is EtsAliasType -> originalType.unsupportedInitialDomain()
    // Intersections use each member's sort; unions use the existing unresolved input domain instead.
    is EtsIntersectionType -> types.firstNotNullOfOrNull { it.unsupportedInitialDomain() }
    else -> null
}
