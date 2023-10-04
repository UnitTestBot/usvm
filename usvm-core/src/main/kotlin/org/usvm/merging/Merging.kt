package org.usvm.merging

interface UMerger<Entity, By> {
    /**
     * @returns Merged entity or null if [left] and [right] are non-mergeable
     */
    fun merge(left: Entity, right: Entity, by: By): Entity?
}

interface UMergeable<Entity, By> {
    fun mergeWith(other: Entity, by: By): Entity?
}

