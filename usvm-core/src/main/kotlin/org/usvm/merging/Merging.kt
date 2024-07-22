package org.usvm.merging

import org.usvm.collections.immutable.internal.MutabilityOwnership

interface UMergeable<Entity, By> {
    /**
     * @return Merged entity or `null` if `this` and [other] are non-mergeable.
     */
    fun mergeWith(other: Entity, by: By): Entity?
}

interface UOwnedMergeable<Entity, By> {
    /**
     * @return Merged entity with [mergedOwnership] as ownership or `null` if `this` and [other] are non-mergeable.
     * Changes [this] and [other] ownerships to [thisOwnership] and [otherOwnership] respectively.
     */
    fun mergeWith(
        other: Entity,
        by: By,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership,
    ): Entity?
}
