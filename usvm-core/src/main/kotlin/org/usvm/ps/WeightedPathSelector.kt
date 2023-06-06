package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.util.UPriorityCollection

open class WeightedPathSelector<State, Weight>(priorityCollectionFactory: () -> UPriorityCollection<State, Weight>, private val weighter: StateWeighter<State, Weight>) : UPathSelector<State> {

    private val priorityCollection = priorityCollectionFactory()

    override fun isEmpty(): Boolean = priorityCollection.count == 0

    override fun peek(): State = priorityCollection.peek()

    override fun update(state: State) = priorityCollection.update(state, weighter.weight(state))

    override fun add(states: Collection<State>) {
        for (state in states) {
            priorityCollection.add(state, weighter.weight(state))
        }
    }

    override fun remove(state: State) = priorityCollection.remove(state)
}
