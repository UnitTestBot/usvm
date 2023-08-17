package org.usvm

import org.usvm.memory.URegionHeap

interface UMerger<Entity> {
    /**
     * @returns Merged entity or null if [left] and [right] are non-mergeable
     */
    fun merge(left: Entity, right: Entity): Entity?
}

class URegionHeapMerger<FieldDescr, ArrayType> : UMerger<URegionHeap<FieldDescr, ArrayType>> {
    // Never merge for now
    override fun merge(left: URegionHeap<FieldDescr, ArrayType>, right: URegionHeap<FieldDescr, ArrayType>) = null
}

open class UStateMerger<State : UState<*, *, *, *, *, State>> : UMerger<State> {
    // Never merge for now
    override fun merge(left: State, right: State) = null
}
