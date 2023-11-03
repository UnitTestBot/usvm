package org.usvm.collection.set

interface USymbolicSetUnionElements<DstElement> {
    fun collectSetElements(elements: USymbolicSetElementsCollector.Elements<DstElement>)
}
