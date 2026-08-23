package org.usvm.ts.pbt.registry

import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.validation.requireValid
import org.usvm.ts.pbt.validation.validatePropertyDefinition

/** Ordered collection of validated Kotlin property definitions with unique stable IDs. */
class PropertyRegistry(properties: List<PropertyDefinition>) {
    val properties: List<PropertyDefinition> = properties.toList()

    private val propertiesById: Map<PropertyId, PropertyDefinition>

    init {
        this.properties.forEach { property ->
            requireValid(validatePropertyDefinition(property))
        }
        rejectDuplicateIds(this.properties)
        propertiesById = this.properties.associateBy(PropertyDefinition::id)
    }

    /** Returns the property identified by [id], or reports all IDs available in this registry. */
    operator fun get(id: PropertyId): PropertyDefinition = propertiesById[id]
        ?: throw UnknownPropertyIdException(
            propertyId = id,
            availablePropertyIds = properties.map(PropertyDefinition::id),
        )

    companion object {
        /** Combines registries in input order and validates IDs across registry boundaries. */
        fun combine(registries: List<PropertyRegistry>): PropertyRegistry = PropertyRegistry(
            registries.flatMap(PropertyRegistry::properties),
        )
    }
}

/** Thrown when the same property ID occurs at multiple registry positions. */
class DuplicatePropertyIdException(
    val propertyId: PropertyId,
    val positions: List<Int>,
) : IllegalArgumentException(
    "Duplicate property ID ${propertyId.value} at positions ${positions.joinToString()}",
)

/** Thrown when a caller selects a property that is absent from a registry. */
class UnknownPropertyIdException(
    val propertyId: PropertyId,
    val availablePropertyIds: List<PropertyId>,
) : NoSuchElementException(
    "Unknown property ID ${propertyId.value}; available IDs: ${availablePropertyIds.joinToString()}",
)

private fun rejectDuplicateIds(properties: List<PropertyDefinition>) {
    val positionsById = properties
        .mapIndexed { index, property -> property.id to index }
        .groupBy(keySelector = Pair<PropertyId, Int>::first, valueTransform = Pair<PropertyId, Int>::second)
    val duplicate = positionsById
        .filterValues { positions -> positions.size > 1 }
        .minByOrNull { (id, _) -> id.value }
        ?: return
    throw DuplicatePropertyIdException(duplicate.key, duplicate.value)
}
