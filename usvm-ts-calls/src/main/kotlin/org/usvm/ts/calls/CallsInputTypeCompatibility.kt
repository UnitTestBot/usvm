package org.usvm.ts.calls

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.util.EtsHierarchy
import org.usvm.util.InputPropertyResolution
import org.usvm.util.componentType
import org.usvm.util.declaredInstanceProperty
import org.usvm.util.resolveInstanceProperty

/** Rejects bindings whose frozen domain would replace a source parameter with an incompatible runtime kind. */
internal fun callsInputTypePreflight(
    inputs: List<PropertyInput>,
    bindings: List<EtsInputBinding>,
    method: EtsMethod,
    scene: EtsScene,
): String? {
    val hierarchy = EtsHierarchy(scene)
    val domainsByName = inputs.associate { input -> input.name to input.domain }
    val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
    for (binding in bindings) {
        val limitation = domainsByName.getValue(binding.propertyInputName).inputTypeLimitation(
            type = binding.parameter.type,
            genericParameters = genericParameters,
            path = "inputs.${binding.propertyInputName}",
            hierarchy = hierarchy,
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
    hierarchy: EtsHierarchy,
    visitedGenerics: Set<String> = emptySet(),
): String? {
    if (this is OptionalDomain) {
        val valueLimitation = value.inputTypeLimitation(type, genericParameters, path, hierarchy, visitedGenerics)
        if (valueLimitation != null) return valueLimitation

        return ConstantDomain(nil).inputTypeLimitation(type, genericParameters, "$path.nil", hierarchy, visitedGenerics)
    }

    val constant = (this as? ConstantDomain)?.value
    val compatible = when (type) {
        EtsAnyType, EtsUnknownType -> {
            true
        }
        EtsBooleanType -> {
            this == BooleanDomain || constant is JsConcreteValue.Boolean
        }
        EtsNumberType -> {
            this is NumberDomain || this is IntegerDomain || constant is JsConcreteValue.Number
        }
        EtsStringType -> {
            this is StringDomain || constant is JsConcreteValue.String
        }
        is EtsNumberLiteralType -> {
            matchesNumberLiteral(type, constant)
        }
        is EtsStringLiteralType -> {
            val matchesEmptyStringDomain = this is StringDomain &&
                minLength == 0 && maxLength == 0 && type.value.isEmpty()
            val matchesConstant = constant is JsConcreteValue.String && constant.value == type.value

            matchesEmptyStringDomain || matchesConstant
        }
        is EtsBooleanLiteralType -> {
            constant is JsConcreteValue.Boolean && constant.value == type.value
        }
        EtsNullType -> {
            constant == JsConcreteValue.Null
        }
        EtsUndefinedType -> {
            constant == JsConcreteValue.Undefined
        }
        is EtsGenericType -> {
            if (type.typeName in visitedGenerics) {
                return "$path: recursive generic bound ${type.typeName} is not supported"
            }

            val upperBound = type.constraint ?: genericParameters[type.typeName]?.constraint ?: EtsUnknownType
            return inputTypeLimitation(upperBound, genericParameters, path, hierarchy, visitedGenerics + type.typeName)
        }
        is EtsAliasType -> {
            return inputTypeLimitation(type.originalType, genericParameters, path, hierarchy, visitedGenerics)
        }
        is EtsArrayType -> {
            val isSupportedArray = this is ArrayDomain && type.dimensions >= 1
            if (isSupportedArray) {
                return (this as ArrayDomain).element.inputTypeLimitation(
                    type = type.componentType,
                    genericParameters = genericParameters,
                    path = "$path[]",
                    hierarchy = hierarchy,
                    visitedGenerics = visitedGenerics,
                )
            }
            false
        }
        is EtsClassType, is EtsUnclearRefType -> {
            return objectFieldTypeLimitation(
                type = type as EtsRefType,
                genericParameters = genericParameters,
                path = path,
                hierarchy = hierarchy,
                visitedGenerics = visitedGenerics,
            )
        }
        is EtsUnionType -> {
            type.types.any { member ->
                inputTypeLimitation(
                    type = member,
                    genericParameters = genericParameters,
                    path = path,
                    hierarchy = hierarchy,
                    visitedGenerics = visitedGenerics,
                ) == null
            }
        }
        is EtsIntersectionType -> {
            return intersectionTypeLimitation(
                type = type,
                genericParameters = genericParameters,
                path = path,
                hierarchy = hierarchy,
                visitedGenerics = visitedGenerics,
            )
        }
        else -> {
            false
        }
    }

    return if (compatible) null else "$path: frozen domain $this is not supported for declared type $type"
}

private fun PropertyDomain.matchesNumberLiteral(
    type: EtsNumberLiteralType,
    constant: JsConcreteValue?,
): Boolean = when {
    this is NumberDomain -> !allowNaN && min.toDouble() == type.value && max.toDouble() == type.value
    this is IntegerDomain -> min.toDouble() == type.value && max.toDouble() == type.value
    constant is JsConcreteValue.Number -> constant.toDouble() == type.value
    else -> false
}

private fun PropertyDomain.intersectionTypeLimitation(
    type: EtsIntersectionType,
    genericParameters: Map<String, EtsGenericType>,
    path: String,
    hierarchy: EtsHierarchy,
    visitedGenerics: Set<String>,
): String? {
    val members = type.types.flatMap { member ->
        member.intersectionMembers(genericParameters, visitedGenerics)
    }
    if (this !is ObjectDomain) {
        for (member in members) {
            val limitation = inputTypeLimitation(member, genericParameters, path, hierarchy, visitedGenerics)
            if (limitation != null) return limitation
        }

        return null
    }

    val objectMembers = members.filter { member -> member is EtsClassType || member is EtsUnclearRefType }
    val otherMembers = members - objectMembers.toSet()
    for (member in otherMembers) {
        val limitation = inputTypeLimitation(member, genericParameters, path, hierarchy, visitedGenerics)
        if (limitation != null) return limitation
    }

    for ((name, domain) in fields) {
        val fieldPath = "$path.$name"
        val resolutions = objectMembers.map { member ->
            resolveInstanceProperty(member, name, hierarchy)
        }
        val hasUnresolvedDeclaration = resolutions.any { resolution ->
            resolution == InputPropertyResolution.Unresolved
        }
        if (hasUnresolvedDeclaration) {
            return "$fieldPath: cannot resolve the declared field type in $type"
        }

        val declarations = resolutions.filterIsInstance<InputPropertyResolution.Declared>()
        if (objectMembers.isNotEmpty() && declarations.isEmpty()) {
            return "$fieldPath: cannot resolve the declared field type in $type"
        }

        for (declaration in declarations) {
            val limitation = domain.inputTypeLimitation(
                type = declaration.property.type,
                genericParameters = genericParameters,
                path = fieldPath,
                hierarchy = hierarchy,
                visitedGenerics = visitedGenerics,
            )
            if (limitation != null) return limitation
        }
    }

    return null
}

private fun EtsType.intersectionMembers(
    genericParameters: Map<String, EtsGenericType>,
    visitedGenerics: Set<String>,
): List<EtsType> = when (this) {
    is EtsIntersectionType -> {
        types.flatMap { member ->
            member.intersectionMembers(genericParameters, visitedGenerics)
        }
    }
    is EtsAliasType -> {
        originalType.intersectionMembers(genericParameters, visitedGenerics)
    }
    is EtsGenericType -> {
        if (typeName in visitedGenerics) {
            listOf(this)
        } else {
            val upperBound = constraint ?: genericParameters[typeName]?.constraint ?: EtsUnknownType
            upperBound.intersectionMembers(genericParameters, visitedGenerics + typeName)
        }
    }
    else -> {
        listOf(this)
    }
}

private fun PropertyDomain.objectFieldTypeLimitation(
    type: EtsRefType,
    genericParameters: Map<String, EtsGenericType>,
    path: String,
    hierarchy: EtsHierarchy,
    visitedGenerics: Set<String>,
): String? {
    if (this !is ObjectDomain) {
        return "$path: frozen domain $this requires a compatible object domain for declared type $type"
    }

    for ((name, domain) in fields) {
        val fieldPath = "$path.$name"
        val fieldType = declaredInstanceProperty(type, name, hierarchy)?.type
            ?: return "$fieldPath: cannot resolve the declared field type in $type"
        val limitation = domain.inputTypeLimitation(
            type = fieldType,
            genericParameters = genericParameters,
            path = fieldPath,
            hierarchy = hierarchy,
            visitedGenerics = visitedGenerics,
        )
        if (limitation != null) return limitation
    }

    return null
}
