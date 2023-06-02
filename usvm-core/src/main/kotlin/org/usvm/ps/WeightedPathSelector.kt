package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.util.UPriorityCollection

open class WeightedPathSelector<State, Weight>(priorityCollectionFactory: () -> UPriorityCollection<State, Weight>, private val weight: (State) -> Weight) : UPathSelector<State> {

    private val priorityQueue = priorityCollectionFactory()

    override fun isEmpty(): Boolean = priorityQueue.count == 0

    override fun peek(): State = priorityQueue.peek()

    override fun update(state: State) = priorityQueue.update(state, weight(state))

    override fun add(states: Collection<State>) {
        for (state in states) {
            priorityQueue.add(state, weight(state))
        }
    }

    override fun remove(state: State) = priorityQueue.remove(state)
}
