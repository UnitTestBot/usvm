package org.usvm.machine.types

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType

/** Uses the generic upper bound for input constraints; a default is not a bound on inferred arguments. */
internal fun EtsType.initialInputType(
    genericParameters: Map<String, EtsGenericType>,
    visitedGenerics: Set<String> = emptySet(),
): EtsType = when (this) {
    is EtsGenericType -> {
        if (typeName in visitedGenerics) {
            this
        } else {
            val upperBound = constraint ?: genericParameters[typeName]?.constraint ?: EtsUnknownType
            upperBound.initialInputType(genericParameters, visitedGenerics + typeName)
        }
    }

    is EtsArrayType -> {
        val resolvedElementType = elementType.initialInputType(genericParameters, visitedGenerics)
        copy(elementType = resolvedElementType)
    }
    else -> this
}
