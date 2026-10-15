package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.algorithms.UPriorityCollection

/**
 * [UPathSelector] implementation which selects high-priority states from [UPriorityCollection] prioritized with
 * specified [Weight].
 *
 * @param priorityCollectionFactory function to create a priority collection for states.
 * @param weighter [StateWeighter] used to get states' weight which is used as priority in collection.
 * @param weightRevision invalidates all queued weights when shared guidance changes between steps.
 */
open class WeightedPathSelector<State, Weight>(
    priorityCollectionFactory: () -> UPriorityCollection<State, Weight>,
    private val weighter: StateWeighter<State, Weight>,
    private val weightRevision: (() -> Long)? = null,
) : UPathSelector<State> {

    private val priorityCollection = priorityCollectionFactory()
    private var currentWeightRevision = weightRevision?.invoke()

    override fun isEmpty(): Boolean = priorityCollection.count == 0

    override fun peek(): State {
        val revision = weightRevision?.invoke()
        if (revision != currentWeightRevision) {
            val states = drainPriorityCollection().toList()
            add(states)
            currentWeightRevision = revision
        }

        return priorityCollection.peek()
    }

    override fun update(state: State) = priorityCollection.update(state, weighter.weight(state))

    override fun add(states: Collection<State>) {
        for (state in states) {
            priorityCollection.add(state, weighter.weight(state))
        }
    }

    override fun remove(state: State) = priorityCollection.remove(state)

    override fun drain(): Sequence<State> = drainPriorityCollection()

    private fun drainPriorityCollection(): Sequence<State> = sequence {
        // Draining stopped work must not recalculate weights or consult external goals.
        while (priorityCollection.count > 0) {
            val state = priorityCollection.peek()
            priorityCollection.remove(state)
            yield(state)
        }
    }
}
