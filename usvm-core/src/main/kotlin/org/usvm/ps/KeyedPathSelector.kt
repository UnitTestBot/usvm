package org.usvm.ps

import org.usvm.UPathSelector

/**
 * [UPathSelector] modification which allows grouping states by generic keys.
 */
abstract class KeyedPathSelector<State, Key>(private val getKey: (State) -> Key) : UPathSelector<State> {

    protected val pathSelectors = HashMap<Key, UPathSelector<State>>()

    override fun update(state: State) {
        val key = getKey(state)
        pathSelectors[key]?.update(state) ?: throw IllegalStateException("Trying to update state with unknown key")
    }

    override fun add(states: Collection<State>) {
        states.groupBy(getKey).forEach { (key, states) ->
            pathSelectors[key]?.add(states) ?: throw IllegalStateException("Trying to add states with unknown key")
        }
    }

    override fun remove(state: State) {
        val key = getKey(state)
        pathSelectors[key]?.remove(state) ?: throw IllegalStateException("Trying to remove state with unknown key")
    }

    fun removeKey(key: Key) {
        pathSelectors.remove(key)
    }
}
