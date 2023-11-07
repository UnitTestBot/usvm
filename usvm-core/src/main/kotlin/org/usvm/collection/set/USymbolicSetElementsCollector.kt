package org.usvm.collection.set

import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isFalse
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.USymbolicCollectionUpdates
import org.usvm.memory.UUpdateNode
import java.util.IdentityHashMap

class USymbolicSetElementsCollector<Element> private constructor()
    : UMemoryUpdatesVisitor<Element, UBoolSort, USymbolicSetElementsCollector.Elements<Element>> {

    override fun visitSelect(result: Elements<Element>, key: Element): UExpr<UBoolSort> {
        error("Unexpected reading")
    }

    override fun visitInitialValue(): Elements<Element> = Elements(elements = mutableListOf(), isInput = false)

    override fun visitUpdate(
        previous: Elements<Element>,
        update: UUpdateNode<Element, UBoolSort>
    ): Elements<Element> {
        if (update.guard.isFalse) return previous

        when (update) {
            is UPinpointUpdateNode -> previous.elements.add(update.key)
            is URangedUpdateNode<*, *, Element, UBoolSort> -> {
                val unionAdapter = update.adapter
                check(unionAdapter is USymbolicSetUnionElements<*>) {
                    "Unexpected adapter: $unionAdapter"
                }
                @Suppress("UNCHECKED_CAST")
                unionAdapter as USymbolicSetUnionElements<Element>

                unionAdapter.collectSetElements(previous)
            }
        }

        return previous
    }

    /**
     * All set elements (added and removed).
     * Set elements marked as [isInput] when:
     * 1. The original set is input
     * 2. The original set is concrete, but has been united with an input set.
     * */
    class Elements<Element>(
        val elements: MutableList<Element>,
        var isInput: Boolean,
    )

    companion object {
        private val instance by lazy { USymbolicSetElementsCollector<Nothing>() }
        fun <Element, Sort : USort> collect(updates: USymbolicCollectionUpdates<Element, Sort>): Elements<Element> {
            @Suppress("UNCHECKED_CAST")
            val visitor = instance as UMemoryUpdatesVisitor<Element, Sort, Elements<Element>>
            return updates.accept(visitor, IdentityHashMap())
        }
    }
}
