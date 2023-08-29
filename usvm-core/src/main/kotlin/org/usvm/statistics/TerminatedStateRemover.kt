package org.usvm.statistics

import org.usvm.UState

/**
 * This observer is devoted to reduce memory consumption
 * by removing terminated states from the path trie.
 *
 * If you do not add this observer into the machine you use,
 * it won't remove terminated states from the path trie.
 * It costs additional memory, but might be useful for debug purposes.
 */
class TerminatedStateRemover<State : UState<*, *, *, *, State>> : UMachineObserver<State> {
    override fun onStateTerminated(state: State) {
        state.pathLocation.states.remove(state)
    }
}
