package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.algorithms.DeterministicPriorityCollection

/**
 * Caller-confirmed coverage changes priorities, never removes states. Updates belong on the analysis thread.
 * [distance] defines statement, edge or other goal semantics; [UInt.MAX_VALUE] means unreachable.
 */
class CoverageGuidedPathSelector<State, Target>(
    targets: Collection<Target>,
    distance: (State, Target) -> UInt,
) : UPathSelector<State> {
    private val knownTargets = targets.toMutableSet()
    private val pendingTargets = targets.toMutableSet()
    private var prioritiesChanged = false
    private val selector = WeightedPathSelector<State, UInt>(
        priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
        weighter = { state -> pendingTargets.minOfOrNull { target -> distance(state, target) } ?: UInt.MAX_VALUE },
    )

    val uncoveredTargets: Set<Target>
        get() = pendingTargets.toSet()

    /** Registers newly identified goals; previously covered goals stay covered. */
    fun addTargets(targets: Collection<Target>): Boolean {
        val additions = targets.filter { knownTargets.add(it) }
        if (additions.isEmpty()) return false

        pendingTargets.addAll(additions)
        prioritiesChanged = true
        return true
    }

    /** Unknown goals are ignored. Changed priorities become visible on the next [peek]. */
    fun markCovered(targets: Collection<Target>): Boolean {
        val changed = pendingTargets.removeAll(targets.toSet())
        if (changed) prioritiesChanged = true

        return changed
    }

    override fun isEmpty(): Boolean = selector.isEmpty()

    override fun peek(): State {
        if (prioritiesChanged) {
            val states = selector.drain().toList()
            selector.add(states)
            prioritiesChanged = false
        }

        return selector.peek()
    }

    override fun add(states: Collection<State>) = selector.add(states)

    override fun update(state: State) = selector.update(state)

    override fun remove(state: State) = selector.remove(state)

    // Bypass pending priority updates when collecting stopped work.
    override fun drain(): Sequence<State> = selector.drain()
}
