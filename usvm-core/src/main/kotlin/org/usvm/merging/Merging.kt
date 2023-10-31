package org.usvm.merging

interface UMergeable<Entity, By> {
    /**
     * @return Merged entity or `null` if `this` and [other] are non-mergeable.
     */
    fun mergeWith(other: Entity, by: By): Entity?
}
