package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.algorithms.UPriorityCollection

/**
 * [UPathSelector] implementation which selects high-priority states from [UPriorityCollection] prioritized with
 * specified [Weight].
 *
 * @param priorityCollectionFactory function to create a priority collection for states.
 * @param weighter [StateWeighter] used to get states' weight which is used as priority in collection.
 */
open class WeightedPathSelector<State, Weight>(
    priorityCollectionFactory: () -> UPriorityCollection<State, Weight>,
    private val weighter: StateWeighter<State, Weight>
) : UPathSelector<State> {

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
