package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.util.get

interface StateGuide<State, Target> {
    fun isStuck(state: State): Boolean
    fun getTargets(state: State): Sequence<Target>
    fun hasReached(state: State, target: Target): Boolean
}

private data class StateTargets<Target>(val targets: Set<Target>)

class GuidedPathSelector<State : UState<*, *, *, *>, Target>(
    private val guide: StateGuide<State, Target>,
    private val basePathSelector: UPathSelector<State>,
    private val targetedPathSelectorFactory: (Target) -> UPathSelector<State>
) : UPathSelector<State> {
    private val targetedPathSelectors = mutableMapOf<Target, UPathSelector<State>>()
    private val targets = mutableListOf<Target>()
    private var counter = 0

    private fun getTargetedPathSelector(target: Target): UPathSelector<State> {
        return targetedPathSelectors.computeIfAbsent(target, targetedPathSelectorFactory)
    }

    override fun isEmpty(): Boolean {
        return basePathSelector.isEmpty() && targetedPathSelectors.values.all { it.isEmpty() }
    }

    override fun peek(): State {
        return targetedPathSelectors.getValue(targets[counter]).peek()
    }

    override fun update(state: State) {
        basePathSelector.update(state)

        val stateTargets = state.properties.get<StateTargets<Target>>() ?: return
        val guideTargets = if (guide.isStuck(state)) guide.getTargets(state) else emptySequence()

        val allTargets = stateTargets.targets.plus(guideTargets).groupBy(targetedPathSelectors::containsKey)
        val existingTargets = allTargets[true] ?: emptyList()
        val newTargets = allTargets[false] ?: emptyList()

        for (newTarget in newTargets) {
            val targetedPathSelector = getTargetedPathSelector(newTarget)
            targetedPathSelector.add(listOf(state))
        }

        for (existingTarget in existingTargets) {
            val targetedPathSelector = getTargetedPathSelector(existingTarget)

            if (guide.hasReached(state, existingTarget)) {
                targetedPathSelector.remove(state)
                if (targetedPathSelector.isEmpty()) {
                    targetedPathSelectors.remove(existingTarget)
                }
            } else {
                targetedPathSelector.update(state)
            }
        }
    }

    override fun add(states: Collection<State>) {
        basePathSelector.add(states)
    }

    override fun remove(state: State) {
        basePathSelector.remove(state)
        targetedPathSelectors.values.forEach { it.remove(state) }
    }
}
