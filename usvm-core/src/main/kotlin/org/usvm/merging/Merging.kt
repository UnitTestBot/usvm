package org.usvm.merging

interface UMerger<Entity, By> {
    /**
     * @returns Merged entity or `null` if [left] and [right] are non-mergeable.
     */
    fun merge(left: Entity, right: Entity, by: By): Entity?
}

interface UMergeable<Entity, By> {
    /**
     * @return Merged entity or `null` if `this` and [other] are non-mergeable.
     */
    fun mergeWith(other: Entity, by: By): Entity?
}

