package org.usvm.machine.types

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsClassValueType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.util.humanReadableSignature

/**
 * Rejects input shapes for which we cannot generate a faithful constructor-valued witness.
 *
 * A class constructor is a function object, not an instance of that class. The current input generator would
 * materialize constructor-typed values as ordinary instances and lose their callable identity. Inspect nested
 * parameter types and instance fields before starting analysis so that this gap has an explicit unsupported outcome.
 * This is an entrypoint-wide bound: an unsupported field excludes the method even if its body does not read it.
 */
internal class TsConstructorInputGuard(scene: EtsScene) {
    private val classesBySignature = scene.projectAndSdkClasses.associateBy { it.signature }

    fun unsupportedReason(method: EtsMethod): String? {
        val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
        val unsupportedParameter = method.parameters.firstNotNullOfOrNull { parameter ->
            val gap = parameter.type.constructorInputGap(genericParameters, classesBySignature)
            gap?.let { parameter to it }
        } ?: return null
        val (parameter, gap) = unsupportedParameter

        return "${gap.description} '${parameter.name}' in ${method.humanReadableSignature} is not modeled"
    }
}

private enum class ConstructorInputGap(val description: String) {
    CONSTRUCTOR_VALUE("Constructor-typed parameter"),
    UNRESOLVED_INHERITED_GENERIC("Unresolved inherited generic field in parameter"),
}

// Visited sets describe the current traversal path, so recursive classes and generic constraints terminate
// without hiding a constructor-valued sibling field. Static fields do not belong to an input object's payload.
private fun EtsType.constructorInputGap(
    genericParameters: Map<String, EtsGenericType>,
    classesBySignature: Map<EtsClassSignature, EtsClass>,
    visitedGenerics: Set<String> = emptySet(),
    visitedClasses: Set<EtsClassSignature> = emptySet(),
): ConstructorInputGap? = when (this) {
    is EtsClassValueType -> ConstructorInputGap.CONSTRUCTOR_VALUE
    is EtsUnionType -> types.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsIntersectionType -> types.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsTupleType -> types.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsArrayType -> elementType.constructorInputGap(
        genericParameters,
        classesBySignature,
        visitedGenerics,
        visitedClasses,
    )
    is EtsClassType -> constructorInputGapInClass(
        genericParameters,
        classesBySignature,
        visitedGenerics,
        visitedClasses,
    )
    is EtsUnclearRefType -> typeParameters.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsAliasType -> originalType.constructorInputGap(
        genericParameters,
        classesBySignature,
        visitedGenerics,
        visitedClasses,
    )
    is EtsGenericType -> {
        if (typeName in visitedGenerics) {
            null
        } else {
            val declaration = genericParameters[typeName]
            listOfNotNull(constraint, defaultType, declaration?.constraint, declaration?.defaultType)
                .firstNotNullOfOrNull {
                    it.constructorInputGap(
                        genericParameters,
                        classesBySignature,
                        visitedGenerics + typeName,
                        visitedClasses,
                    )
                }
        }
    }
    else -> null
}

private fun EtsClassType.constructorInputGapInClass(
    genericParameters: Map<String, EtsGenericType>,
    classesBySignature: Map<EtsClassSignature, EtsClass>,
    visitedGenerics: Set<String>,
    visitedClasses: Set<EtsClassSignature>,
): ConstructorInputGap? {
    val typeArgumentGap = typeParameters.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    if (typeArgumentGap != null) {
        return typeArgumentGap
    }

    if (signature in visitedClasses) {
        return null
    }

    val clazz = classesBySignature[signature] ?: return null
    val nextVisitedClasses = visitedClasses + signature
    val fieldGap = clazz.fields.firstNotNullOfOrNull { field ->
        if (field.modifiers.isStatic) return@firstNotNullOfOrNull null

        field.type.constructorInputGap(
            genericParameters,
            classesBySignature,
            visitedGenerics,
            nextVisitedClasses,
        )
    }
    if (fieldGap != null) {
        return fieldGap
    }

    val superClass = clazz.superClass ?: return null
    val superClasses = if (superClass.file == EtsFileSignature.UNKNOWN) {
        classesBySignature.values.filter { it.name == superClass.name }
    } else {
        listOfNotNull(classesBySignature[superClass])
    }

    return superClasses.firstNotNullOfOrNull { parent ->
        val parentGap = EtsClassType(parent.signature).constructorInputGap(
            genericParameters,
            classesBySignature,
            visitedGenerics,
            nextVisitedClasses,
        )
        if (parentGap != null) return@firstNotNullOfOrNull parentGap

        // The frontend omits extends type arguments. Even an apparent numeric specialization may hide a
        // constructor in an inherited T, so reject this shape until superclass arguments are available.
        val parentGenerics = parent.typeParameters.filterIsInstance<EtsGenericType>()
            .mapTo(mutableSetOf()) { it.typeName }
        val unresolvedGenericField = parent.fields.any { field ->
            !field.modifiers.isStatic && field.type.containsGeneric(parentGenerics)
        }
        if (unresolvedGenericField) ConstructorInputGap.UNRESOLVED_INHERITED_GENERIC else null
    }
}

private fun EtsType.containsGeneric(names: Set<String>): Boolean = when (this) {
    is EtsGenericType -> typeName in names
    is EtsUnionType -> types.any { it.containsGeneric(names) }
    is EtsIntersectionType -> types.any { it.containsGeneric(names) }
    is EtsTupleType -> types.any { it.containsGeneric(names) }
    is EtsArrayType -> elementType.containsGeneric(names)
    is EtsClassType -> typeParameters.any { it.containsGeneric(names) }
    is EtsUnclearRefType -> typeParameters.any { it.containsGeneric(names) }
    is EtsAliasType -> originalType.containsGeneric(names)
    else -> false
}
