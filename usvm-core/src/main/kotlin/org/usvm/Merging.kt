package org.usvm

interface UMerger<Entity> {
    /**
     * @returns Merged entity or null if [left] and [right] are non-mergeable
     */
    fun merge(left: Entity, right: Entity): Entity?
}

open class UStateMerger<Type, Method, Statement>: UMerger<UState<Type, Method, Statement>> {
    override fun merge(left: UState<Type, Method, Statement>, right: UState<Type, Method, Statement>) =
        null // Never merge for now
}
