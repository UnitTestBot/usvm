package org.usvm.machine.types

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassValueType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.util.humanReadableSignature

/**
 * Bounds inputs that themselves require a callable constructor witness, rather than an ordinary object.
 * Instance inputs are structural field maps: neither their class declarations nor generic arguments imply
 * that every declared field is used. Check those fields only when their initial values are actually read.
 */
internal class TsConstructorInputGuard {
    fun unsupportedReason(method: EtsMethod): String? {
        val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
        val parameter = method.parameters.firstOrNull {
            it.type.requiresConstructorWitness(genericParameters)
        } ?: return null

        return "Constructor-typed parameter '${parameter.name}' in ${method.humanReadableSignature} is not modeled"
    }
}

/** Inspect the value's own type, without traversing instance declarations or their type arguments. */
internal fun EtsType.requiresConstructorWitness(
    genericParameters: Map<String, EtsGenericType> = emptyMap(),
    visitedGenerics: Set<String> = emptySet(),
): Boolean = when (this) {
    is EtsClassValueType -> true
    is EtsUnionType -> types.any { it.requiresConstructorWitness(genericParameters, visitedGenerics) }
    is EtsIntersectionType -> types.any { it.requiresConstructorWitness(genericParameters, visitedGenerics) }
    is EtsTupleType -> types.any { it.requiresConstructorWitness(genericParameters, visitedGenerics) }
    is EtsArrayType -> elementType.requiresConstructorWitness(genericParameters, visitedGenerics)
    is EtsAliasType -> originalType.requiresConstructorWitness(genericParameters, visitedGenerics)
    is EtsGenericType -> {
        if (typeName in visitedGenerics) {
            false
        } else {
            val declaration = genericParameters[typeName]
            listOfNotNull(constraint, defaultType, declaration?.constraint, declaration?.defaultType).any {
                it.requiresConstructorWitness(genericParameters, visitedGenerics + typeName)
            }
        }
    }
    else -> false
}
