package org.usvm.statistics

import org.usvm.UState

/**
 * Maintains information about the number of steps made by machine.
 */
class StepsStatistics<Method, State : UState<*, Method, *, *, *, State>> : UMachineObserver<State> {

    /**
     * Total number of steps machine made.
     */
    var totalSteps = 0UL
        private set

    private val methodSteps = mutableMapOf<Method, ULong>()

    /**
     * Returns number of steps machine made during [method] exploration.
     */
    fun getMethodSteps(method: Method) = methodSteps.getOrDefault(method, 0UL)

    override fun onState(parent: State, forks: Sequence<State>) {
        totalSteps++
        methodSteps.merge(parent.entrypoint, 1UL) { current, one -> current + one }
    }
}
