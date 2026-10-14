package org.usvm.util

import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType

/** Resolved own or inherited data-field metadata for a declared receiver type. */
data class InputPropertyDeclaration(val type: EtsType, val optional: Boolean)

/** Keep absent fields distinct from declarations that cannot be resolved consistently. */
sealed interface InputPropertyResolution {
    data class Declared(val property: InputPropertyDeclaration) : InputPropertyResolution
    data object Absent : InputPropertyResolution
    data object Unresolved : InputPropertyResolution
}

/** Resolve receiver-local metadata; independent declarations must agree on type and optionality. */
fun resolveInstanceProperty(
    type: EtsType?,
    name: String,
    hierarchy: EtsHierarchy,
): InputPropertyResolution {
    if (type !is EtsClassType && type !is EtsUnclearRefType) return InputPropertyResolution.Unresolved

    val declarations = hierarchy.classesForType(type).map { receiver ->
        val fieldOwners = hierarchy.getAncestors(receiver).filter { owner ->
            owner.fields.any { !it.isStatic && it.name == name }
        }
        // A class field defines the value even when an implemented interface has a weaker contract.
        val classOwners = fieldOwners.filter { it.category != EtsClassCategory.INTERFACE }
        val owners = classOwners.ifEmpty { fieldOwners }

        // A redeclaration shadows the ancestor's type and optional flag.
        val nearestOwners = owners.filter { owner ->
            owners.none { descendant -> owner != descendant && owner in hierarchy.getAncestors(descendant) }
        }
        val nearestFields = nearestOwners.flatMap { owner ->
            owner.fields.filter { field -> !field.isStatic && field.name == name }
        }
        val nearestDeclarations = nearestFields
            .map { field ->
                InputPropertyDeclaration(field.type, optional = (field as? EtsFieldImpl)?.isOptional == true)
            }
            .distinct()
        when (nearestDeclarations.size) {
            0 -> InputPropertyResolution.Absent
            1 -> InputPropertyResolution.Declared(nearestDeclarations.single())
            else -> InputPropertyResolution.Unresolved
        }
    }
    // An unresolved receiver may denote several classes. Every candidate must agree on one outcome.
    return declarations.distinct().singleOrNull() ?: InputPropertyResolution.Unresolved
}

fun declaredInstanceProperty(
    type: EtsType?,
    name: String,
    hierarchy: EtsHierarchy,
): InputPropertyDeclaration? = when (val resolution = resolveInstanceProperty(type, name, hierarchy)) {
    is InputPropertyResolution.Declared -> resolution.property
    InputPropertyResolution.Absent, InputPropertyResolution.Unresolved -> null
}
