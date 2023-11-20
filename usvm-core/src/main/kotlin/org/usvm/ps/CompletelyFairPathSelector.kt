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
 * Spent time is estimated as timespan between successive peeks.
 *
 * @param initialKeys complete set of [Key]s which will be used with this instance. Operations with states having other keys
 * are not allowed.
 * @param stopwatch [Stopwatch] implementation instance used to measure time between peeks.
 * @param getKey returns key by state. For the same states the same key should be returned.
 * @param basePathSelectorFactory function to create a [UPathSelector] associated with specific key. States
 * with the same key are maintained in path selector created by this function.
 * @param peeksInQuantum number of peeks to switch keys after. Time is measured between the first and the
 * last peek in such series, if this value is greater than 1.
 */
class CompletelyFairPathSelector<State, Key>(
    initialKeys: Set<Key>,
    private val stopwatch: Stopwatch,
    private val getKey: (State) -> Key,
    basePathSelectorFactory: (Key) -> UPathSelector<State>,
    private val peeksInQuantum: UInt = 1U
) : KeyedPathSelector<State, Key> {

    private data class KeysQueueElement<Key>(val key: Key, var elapsed: Duration)
    private val keysQueue = PriorityQueue(
        Comparator
            .comparing<KeysQueueElement<Key>, Duration> { it.elapsed }
    )
    private val pathSelectors = HashMap<Key, UPathSelector<State>>()

    private var peekCounter = 0U

    init {
        require(peeksInQuantum > 0U) { "peeksInQuantum value must be greater than zero" }
        require(initialKeys.any()) { "initialKeys must contain at least one element" }
        stopwatch.reset()
        initialKeys.forEach {
            pathSelectors[it] = basePathSelectorFactory(it)
            keysQueue.add(KeysQueueElement(it, Duration.ZERO))
        }
    }

    private fun startNewQuantum() {
        stopwatch.reset()
        peekCounter = 0U
    }

    override fun isEmpty(): Boolean {
        if (keysQueue.isEmpty()) {
            return true
        }
        return pathSelectors.values.all { it.isEmpty() }
    }

    override fun peek(): State {
        var currentKey = keysQueue.peek()
        var currentPathSelector = pathSelectors[currentKey.key] // Key may not be found if it is removed by removeKey()
        val isCurrentPathSelectorEmpty = currentPathSelector == null || currentPathSelector.isEmpty()
        val quantumEnded = stopwatch.isRunning && peekCounter == peeksInQuantum
        // When peek limit for the current element is reached or there is nothing to peek, start peeking the next one
        if (quantumEnded || isCurrentPathSelectorEmpty) {
            stopwatch.stop()
            keysQueue.poll()
            if (!isCurrentPathSelectorEmpty) {
                currentKey.elapsed += stopwatch.elapsed
                keysQueue.add(currentKey)
            }
            currentKey = keysQueue.peek()
            currentPathSelector = pathSelectors[currentKey.key]
            startNewQuantum()
        }
        peekCounter++
        if (!stopwatch.isRunning) {
            stopwatch.start()
        }
        while (currentPathSelector == null || currentPathSelector.isEmpty()) {
            keysQueue.poll()
            pathSelectors.remove(currentKey.key)
            currentKey = keysQueue.peek()
            currentPathSelector = pathSelectors[currentKey.key]
        }
        return currentPathSelector.peek()
    }

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

    override fun removeKey(key: Key) {
        // Removing from keysQueue is performed by subsequent peek() calls
        pathSelectors.remove(key)
    }
}
