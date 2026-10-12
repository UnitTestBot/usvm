package org.usvm.ts.calls

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain

/** Rejects bindings whose frozen domain would replace a source parameter with an incompatible runtime kind. */
internal fun callsInputTypePreflight(
    inputs: List<PropertyInput>,
    bindings: List<EtsInputBinding>,
    method: EtsMethod,
): String? {
    val domainsByName = inputs.associate { input -> input.name to input.domain }
    val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
    for (binding in bindings) {
        val limitation = domainsByName.getValue(binding.propertyInputName).inputTypeLimitation(
            type = binding.parameter.type,
            genericParameters = genericParameters,
            path = "inputs.${binding.propertyInputName}",
        )
        if (limitation != null) {
            return "$limitation (source parameter ${binding.parameter.name}, stack slot ${binding.stackSlot})"
        }
    }

    return null
}

private fun PropertyDomain.inputTypeLimitation(
    type: EtsType,
    genericParameters: Map<String, EtsGenericType>,
    path: String,
    visitedGenerics: Set<String> = emptySet(),
): String? {
    val compatible = when (type) {
        EtsAnyType, EtsUnknownType -> {
            true
        }
        EtsBooleanType -> {
            this == BooleanDomain
        }
        EtsNumberType -> {
            this is NumberDomain
        }
        EtsStringType -> {
            this is StringDomain
        }
        is EtsNumberLiteralType -> {
            this is NumberDomain && !allowNaN && min.toDouble() == type.value && max.toDouble() == type.value
        }
        is EtsStringLiteralType -> {
            this is StringDomain && minLength == 0 && maxLength == 0 && type.value.isEmpty()
        }
        is EtsGenericType -> {
            if (type.typeName in visitedGenerics) {
                return "$path: recursive generic bound ${type.typeName} is not supported"
            }

            val upperBound = type.constraint ?: genericParameters[type.typeName]?.constraint ?: EtsUnknownType
            return inputTypeLimitation(upperBound, genericParameters, path, visitedGenerics + type.typeName)
        }
        is EtsAliasType -> {
            return inputTypeLimitation(type.originalType, genericParameters, path, visitedGenerics)
        }
        is EtsArrayType -> {
            val isSupportedArray = this is ArrayDomain && type.dimensions == 1
            if (isSupportedArray) {
                return (this as ArrayDomain).element.inputTypeLimitation(
                    type = type.elementType,
                    genericParameters = genericParameters,
                    path = "$path[]",
                    visitedGenerics = visitedGenerics,
                )
            }
            false
        }
        is EtsUnionType -> {
            type.types.any { member -> inputTypeLimitation(member, genericParameters, path, visitedGenerics) == null }
        }
        is EtsIntersectionType -> {
            for (member in type.types) {
                val limitation = inputTypeLimitation(member, genericParameters, path, visitedGenerics)
                if (limitation != null) return limitation
            }
            true
        }
        else -> {
            false
        }
    }

    return if (compatible) null else "$path: frozen domain $this is not supported for declared type $type"
}
