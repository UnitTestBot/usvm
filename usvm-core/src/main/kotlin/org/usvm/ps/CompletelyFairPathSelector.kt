package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.util.Stopwatch
import java.util.*
import kotlin.time.Duration

/**
 * [UPathSelector] implementation which uses strategy similar to Linux Completely Fair Scheduler
 * to switch between states with different [Key]s (for example, different entry point methods).
 * Keys are stored in queue prioritized with estimated time which has already been spent to states with
 * that key. As a result, a key with the lowest time spent is always peeked.
 * Spent time is estimated as timespan between successive peeks. Keys in queue are additionally sorted by [KeyPriority] (for example,
 * current method coverage).
 *
 * @param initialKeys complete set of [Key]s which will be used with this instance. Operations with states having other keys
 * are not allowed.
 * @param stopwatch [Stopwatch] implementation instance used to measure time between peeks.
 * @param getKey returns key by state. For the same states the same key should be returned.
 * @param getKeyPriority returns priority by key. Priority can change over time.
 * @param basePathSelectorFactory function to create a [UPathSelector] associated with specific key. States
 * with the same key are maintained in path selector created by this function.
 * @param peeksInQuantum number of peeks to switch keys after. Time is measured between the first and the
 * last peek in such series, if this value is greater than 1.
 */
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
