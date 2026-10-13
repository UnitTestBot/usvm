package org.usvm.util

import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType

/** Resolved own or inherited data-field metadata for a declared receiver type. */
data class InputPropertyDeclaration(val type: EtsType, val optional: Boolean)

/** Resolve receiver-local metadata; independent declarations must agree on type and optionality. */
fun declaredInstanceProperty(
    type: EtsType?,
    name: String,
    hierarchy: EtsHierarchy,
): InputPropertyDeclaration? {
    if (type !is EtsClassType && type !is EtsUnclearRefType) return null

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
        nearestOwners.flatMap { owner -> owner.fields.filter { !it.isStatic && it.name == name } }
            .map { field ->
                InputPropertyDeclaration(field.type, optional = (field as? EtsFieldImpl)?.isOptional == true)
            }
            .distinct()
            .singleOrNull()
    }
    // An unresolved receiver may denote several classes. Keep metadata only when every candidate agrees.
    return declarations.distinct().singleOrNull()
}
