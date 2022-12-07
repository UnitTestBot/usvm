package org.usvm

interface UMerger<Entity> {
    /**
     * @returns Merged entity or null if [left] and [right] are non-mergeable
     */
    fun merge(left: Entity, right: Entity): Entity?
}

class URegionHeapMerger<FieldDescr, ArrayType>: UMerger<URegionHeap<FieldDescr, ArrayType>> {
    override fun merge(left: URegionHeap<FieldDescr, ArrayType>, right: URegionHeap<FieldDescr, ArrayType>) =
        null // Never merge for now
}

open class UStateMerger<Type, Field, Method, Statement>: UMerger<UState<Type, Field, Method, Statement>> {
    override fun merge(left: UState<Type, Field, Method, Statement>, right: UState<Type, Field, Method, Statement>) =
        null // Never merge for now
}
