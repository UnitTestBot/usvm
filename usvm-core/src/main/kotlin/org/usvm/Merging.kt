package org.usvm

interface UMerger<Entity> {
    /**
     * @returns Merged entity or null if [left] and [right] are non-mergeable
     */
    fun merge(left: Entity, right: Entity): Entity?
}

open class UStateMerger<State : UState<*, *, *, *, State>> : UMerger<State> {
    // Never merge for now
    override fun merge(left: State, right: State) = null
}
