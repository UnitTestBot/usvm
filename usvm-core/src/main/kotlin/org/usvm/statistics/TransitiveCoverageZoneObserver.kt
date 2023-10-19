package org.usvm.statistics

import org.usvm.UState

/**
 * Adds new coverage zones if some state contains an instruction
 * that doesn't belong to any of the already known coverage zones.
 *
 * [ignoreMethod] is a predicate allowing to filter out some methods,
 * e.g., the ones that belong to the system libraries.
 */
class TransitiveCoverageZoneObserver<State : UState<*, *, *, *, *, *>, Method>(
    initialMethod: Method,
    private val methodExtractor: (State) -> Method,
    private val addCoverageZone: (Method) -> Unit,
    private val ignoreMethod: (Method) -> Boolean,
) : UMachineObserver<State> {
    private val collectedMethods: MutableSet<Method> = mutableSetOf(initialMethod)

    override fun onState(parent: State, forks: Sequence<State>) {
        addInstructionsToCover(parent)
        forks.forEach { addInstructionsToCover(it) }
    }

    private fun addInstructionsToCover(state: State) {
        val method = methodExtractor(state)
        if (method in collectedMethods || ignoreMethod(method)) return

        collectedMethods += method
        addCoverageZone(method)
    }
}