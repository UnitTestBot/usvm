package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.util.Stopwatch
import java.util.*
import kotlin.time.Duration

/**
 * [UPathSelector] implementation which uses strategy similar to Linux O(1) scheduler
 * to switch between states with different [Key]s (for example, different entry point methods).
 * Keys are switched in round-robin fashion (so, all keys are guaranteed to be selected). Each key is given
 * an equal time quantum which is calculated using the value returned by [getRemainingTime]. If such quantum is
 * exceeded by some key, quantum for the remaining keys in round is recalculated (decreased equally). From other side,
 * if some key takes less time than the quantum, another round is likely to happen.
 * Spent time is estimated as timespan between successive peeks. Keys in queue are sorted by total time spent and
 * by [KeyPriority] (for example, current method coverage).
 *
 * @param initialKeys complete set of [Key]s which will be used with this instance. Operations with states having other keys
 * are not allowed.
 * @param stopwatch [Stopwatch] implementation instance used to measure time between peeks.
 * @param getRemainingTime returns estimated remaining time value which is used to calculate time quantum per key.
 * @param getKey returns key by state. For the same states the same key should be returned.
 * @param getKeyPriority returns priority by key. Priority can change over time.
 * @param basePathSelectorFactory function to create a [UPathSelector] associated with specific key. States
 * with the same key are maintained in path selector created by this function.
 */
class ConstantTimeFairPathSelector<State, Key, KeyPriority : Comparable<KeyPriority>>(
    initialKeys: Set<Key>,
    private val stopwatch: Stopwatch,
    private val getRemainingTime: () -> Duration,
    private val getKey: (State) -> Key,
    private val getKeyPriority: (Key) -> KeyPriority,
    basePathSelectorFactory: (Key) -> UPathSelector<State>
) : KeyedPathSelector<State, Key> {

    private data class KeysQueueElement<Key, KeyPriority>(val key: Key, var priority: KeyPriority, var elapsed: Duration)
    private val activeQueue = PriorityQueue(
        Comparator
            .comparing<KeysQueueElement<Key, KeyPriority>, Duration> { it.elapsed }
            .thenComparing<KeyPriority> { it.priority }
    )
    private val expiredList = mutableListOf<KeysQueueElement<Key, KeyPriority>>()
    private val pathSelectors = HashMap<Key, UPathSelector<State>>()

    private var currentTimeQuantum: Duration

    init {
        require(initialKeys.any()) { "initialKeys must contain at least one element" }
        stopwatch.reset()
        initialKeys.forEach {
            pathSelectors[it] = basePathSelectorFactory(it)
            activeQueue.add(KeysQueueElement(it, getKeyPriority(it), Duration.ZERO))
        }
        val pathSelectorsCount = pathSelectors.size
        val remainingTime = getRemainingTime()
        currentTimeQuantum = remainingTime / pathSelectorsCount
    }

    private fun peekFromQueues(): KeysQueueElement<Key, KeyPriority> {
        check(activeQueue.isNotEmpty() || expiredList.isNotEmpty()) { "Trying to peek from empty path selector" }
        if (activeQueue.isEmpty()) {
            expiredList.forEach {
                it.priority = getKeyPriority(it.key)
                activeQueue.add(it)
            }
            expiredList.clear()
            val remainingTime = getRemainingTime()
            currentTimeQuantum = remainingTime / activeQueue.size
        }
        return activeQueue.peek()
    }

    override fun isEmpty(): Boolean {
        if (activeQueue.isEmpty() && expiredList.isEmpty()) {
            return true
        }
        return pathSelectors.values.all { it.isEmpty() }
    }

    override fun peek(): State {
        var currentKey = activeQueue.peek()
        var currentPathSelector = pathSelectors[currentKey.key]
        val isCurrentPathSelectorEmpty = currentPathSelector == null || currentPathSelector.isEmpty()
        val quantumEnded = stopwatch.isRunning && stopwatch.elapsed >= currentTimeQuantum
        // When time quantum is exceeded or there is nothing to peek, start peeking the next one
        if (quantumEnded || isCurrentPathSelectorEmpty) {
            stopwatch.stop()
            activeQueue.remove(currentKey)
            if (!isCurrentPathSelectorEmpty) {
                currentKey.elapsed += stopwatch.elapsed
                expiredList.add(currentKey)
            } else {
                pathSelectors.remove(currentKey.key)
            }
            if (stopwatch.elapsed > currentTimeQuantum && activeQueue.isNotEmpty()) {
                currentTimeQuantum -= (stopwatch.elapsed - currentTimeQuantum) / activeQueue.size
            }
            currentKey = peekFromQueues()
            currentPathSelector = pathSelectors[currentKey.key]
            stopwatch.reset()
        }
        if (!stopwatch.isRunning) {
            stopwatch.start()
        }

        while (currentPathSelector == null || currentPathSelector.isEmpty()) {
            activeQueue.remove(currentKey)
            pathSelectors.remove(currentKey.key)
            currentKey = peekFromQueues()
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
