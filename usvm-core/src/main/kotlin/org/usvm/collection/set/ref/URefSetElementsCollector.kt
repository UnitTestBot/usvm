package org.usvm.collection.set.ref

import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.isFalse
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UUpdateNode

internal class URefSetElementsCollector<Element>
    : UMemoryUpdatesVisitor<Element, UBoolSort, URefSetElementsCollector.Elements<Element>> {

    override fun visitSelect(result: Elements<Element>, key: Element): UExpr<UBoolSort> {
        error("Unexpected reading")
    }

    override fun visitInitialValue(): Elements<Element> = Elements(elements = mutableListOf(), input = false)

    override fun visitUpdate(
        previous: Elements<Element>,
        update: UUpdateNode<Element, UBoolSort>
    ): Elements<Element> {
        if (update.guard.isFalse) return previous

        when (update) {
            is UPinpointUpdateNode -> previous.elements.add(update.key)
            is URangedUpdateNode<*, *, Element, UBoolSort> -> {
                check(update.adapter is USymbolicRefSetUnionAdapter<*, *, Element, *>) {
                    "Unexpected adapter: ${update.adapter}"
                }
                update.adapter.collectSetElements(previous)
            }
        }

        return previous
    }

    internal class Elements<Element>(
        val elements: MutableList<Element>,
        var input: Boolean,
    )
}
