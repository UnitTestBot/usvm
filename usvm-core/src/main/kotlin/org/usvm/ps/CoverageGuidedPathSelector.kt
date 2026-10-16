package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.algorithms.DeterministicPriorityCollection

/**
 * Caller-confirmed coverage changes priorities, never removes states. Updates belong on the analysis thread.
 * [distance] defines statement, edge or other goal semantics; [UInt.MAX_VALUE] means unreachable.
 *
 * Weights use a snapshot of uncovered goals. Changes are applied before selection after at least
 * [priorityRefreshInterval] completed selections since the previous priority refresh. A selection completes when
 * its state is updated or removed; repeated [peek] calls do not advance the interval.
 * The default interval is a tuning starting point, not a measured optimum.
 */
class CoverageGuidedPathSelector<State, Target>(
    targets: Collection<Target>,
    distance: (State, Target) -> UInt,
    private val priorityRefreshInterval: Int = DEFAULT_PRIORITY_REFRESH_INTERVAL,
) : UPathSelector<State> {
    init {
        require(priorityRefreshInterval > 0) { "Priority refresh interval must be positive" }
    }

    private val knownTargets = targets.toMutableSet()
    private val pendingTargets = targets.toMutableSet()
    private var priorityTargets: Set<Target> = targets.toSet()
    private val distanceToTarget = distance
    private var prioritiesChanged = false
    private var completedSelections = 0
    private var selectionPending = false
    private var selectedState: State? = null
    private val priorities = DeterministicPriorityCollection<State, UInt>(Comparator.naturalOrder())
    private val selector = WeightedPathSelector<State, UInt>(
        priorityCollectionFactory = { priorities },
        weighter = { state -> weight(state, priorityTargets) },
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

    /** Unknown goals are ignored. Changed priorities are applied at the next eligible selection. */
    fun markCovered(targets: Collection<Target>): Boolean {
        val changed = pendingTargets.removeAll(targets.toSet())
        if (changed) {
            prioritiesChanged = pendingTargets.isNotEmpty()
            if (!prioritiesChanged) {
                // With no goals, ordering is irrelevant and new work needs no distance calculations.
                priorityTargets = emptySet()
            }
        }

        return changed
    }

    override fun isEmpty(): Boolean = selector.isEmpty()

    override fun peek(): State {
        val refreshDue = prioritiesChanged && completedSelections >= priorityRefreshInterval
        if (refreshDue) {
            val refreshedTargets = pendingTargets.toSet()
            priorities.updatePriorities { state -> weight(state, refreshedTargets) }
            priorityTargets = refreshedTargets
            prioritiesChanged = false
            completedSelections = 0
        }

        val state = selector.peek()
        selectedState = state
        selectionPending = true
        return state
    }

    override fun add(states: Collection<State>) = selector.add(states)

    override fun update(state: State) {
        selector.update(state)
        completeSelection(state)
    }

    override fun remove(state: State) {
        selector.remove(state)
        completeSelection(state)
    }

    // Bypass pending priority updates and selection accounting when collecting stopped work.
    override fun drain(): Sequence<State> {
        selectionPending = false
        selectedState = null
        return selector.drain()
    }

    private fun weight(state: State, targets: Set<Target>): UInt =
        targets.minOfOrNull { target -> distanceToTarget(state, target) } ?: UInt.MAX_VALUE

    private fun completeSelection(state: State) {
        val completesSelection = selectionPending && selectedState == state
        if (!completesSelection) return

        if (completedSelections < priorityRefreshInterval) {
            completedSelections++
        }
        selectionPending = false
        selectedState = null
    }

    private companion object {
        const val DEFAULT_PRIORITY_REFRESH_INTERVAL = 128
    }
}
