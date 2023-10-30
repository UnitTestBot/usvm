package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.util.Stopwatch
import java.util.*
import kotlin.time.Duration

class CompletelyFairPathSelector<State, Key, KeyPriority : Comparable<KeyPriority>>(
    initialKeys: Sequence<Key>,
    private val stopwatch: Stopwatch,
    private val getKey: (State) -> Key,
    private val getKeyPriority: (Key) -> KeyPriority,
    basePathSelectorFactory: (Key) -> UPathSelector<State>,
    private val peeksInQuantum: UInt = 1U
) : UPathSelector<State> {

    private data class KeysQueueElement<Key, KeyPriority>(val key: Key, val priority: KeyPriority, val elapsed: Duration)
    private val keysQueue = PriorityQueue(
        Comparator.comparing<KeysQueueElement<Key, KeyPriority>, Duration> { it.elapsed }.thenComparing<KeyPriority> { it.priority }
    )
    private val pathSelectors = HashMap<Key, UPathSelector<State>>()

    private var peekCounter = 0U

    init {
        require(peeksInQuantum > 0U)
        require(initialKeys.any())
        stopwatch.reset()
        initialKeys.forEach {
            pathSelectors[it] = basePathSelectorFactory(it)
            keysQueue.add(KeysQueueElement(it, getKeyPriority(it), Duration.ZERO))
        }
    }

    override fun isEmpty(): Boolean {
        if (keysQueue.isEmpty()) {
            return true
        }
        return pathSelectors.values.all { it.isEmpty() }
    }

    override fun peek(): State {
        var nextKey = keysQueue.peek()
        var nextPathSelector = pathSelectors[nextKey.key] // Key may not be found if it is removed by removeKey()
        val isNextPathSelectorEmpty = nextPathSelector == null || nextPathSelector.isEmpty()
        if (stopwatch.isRunning && peekCounter == peeksInQuantum || isNextPathSelectorEmpty) {
            stopwatch.stop()
            peekCounter = 0U
            keysQueue.poll()
            if (!isNextPathSelectorEmpty) {
                keysQueue.add(
                    nextKey.copy(
                        priority = getKeyPriority(nextKey.key),
                        elapsed = nextKey.elapsed + stopwatch.elapsed
                    )
                )
            }
            nextKey = keysQueue.peek()
            nextPathSelector = pathSelectors[nextKey.key]
            stopwatch.reset()
        }
        if (++peekCounter == 1U) {
            stopwatch.start()
        }
        while (nextPathSelector == null || nextPathSelector.isEmpty()) {
            keysQueue.poll()
            pathSelectors.remove(nextKey.key)
            nextKey = keysQueue.peek()
            nextPathSelector = pathSelectors[nextKey.key]
        }
        return nextPathSelector.peek()
    }

    override fun update(state: State) {
        val key = getKey(state)
        pathSelectors[key]?.update(state) ?: IllegalStateException("Trying to update state with unknown key")
    }

    override fun add(states: Collection<State>) {
        states.groupBy(getKey).forEach { (key, states) ->
            pathSelectors[key]?.add(states) ?: throw IllegalStateException("Trying to add states with unknown key")
        }
    }

    override fun remove(state: State) {
        val key = getKey(state)
        pathSelectors[key]?.remove(state) ?: IllegalStateException("Trying to remove state with unknown key")
    }

    fun removeKey(key: Key) {
        // Key will be removed from queue by peek()
        pathSelectors.remove(key)
    }
}
