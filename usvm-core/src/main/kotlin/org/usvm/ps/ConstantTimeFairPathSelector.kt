package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.util.Stopwatch
import java.util.*
import kotlin.time.Duration

/**
 * [UPathSelector] implementation which uses strategy similar to Linux O(1) scheduler
 * to switch between states with different [Key]s (for example, different entry point methods).
 * Keys are switched in round-robin fashion (so, all keys are guaranteed to be selected). Each key is given
 * an equal time quantum calculated from [getRemainingTime] and bounded by [maxTimeQuantum]. An uncapped overrun is
 * redistributed over the remaining keys; capped turns recompute their share of the actual remaining budget.
 * If a key finishes before its quantum ends, there may be time for another round.
 * Spent time is estimated as timespan between successive peeks. Keys in queue are sorted by [KeyPriority] and total time spent.
 *
 * @param initialKeys complete set of [Key]s which will be used with this instance. Operations with states having other keys
 * are not allowed.
 * @param stopwatch [Stopwatch] implementation instance used to measure time between peeks.
 * @param getRemainingTime returns estimated remaining time value which is used to calculate time quantum per key.
 * @param getKey returns key by state. For the same states the same key should be returned.
 * @param getKeyPriority returns priority by key. Priority can change over time. Smaller values have greater priority.
 * @param basePathSelectorFactory function to create a [UPathSelector] associated with specific key. States
 * with the same key are maintained in path selector created by this function.
 * @param maxTimeQuantum upper bound for each turn. It cannot interrupt an in-flight interpreter step.
 */
class ConstantTimeFairPathSelector<State, Key, KeyPriority : Comparable<KeyPriority>>(
    initialKeys: Set<Key>,
    private val stopwatch: Stopwatch,
    private val getRemainingTime: () -> Duration,
    getKey: (State) -> Key,
    private val getKeyPriority: (Key) -> KeyPriority,
    basePathSelectorFactory: (Key) -> UPathSelector<State>,
    private val maxTimeQuantum: Duration = Duration.INFINITE,
    private val onTimeQuantum: (Key, Duration, Duration) -> Unit = { _, _, _ -> },
) : KeyedPathSelector<State, Key>(getKey) {

    private data class KeysQueueElement<Key, KeyPriority>(val key: Key, var priority: KeyPriority, var elapsed: Duration)
    private val activeQueue = PriorityQueue(
        Comparator
            .comparing<KeysQueueElement<Key, KeyPriority>, KeyPriority> { it.priority }
            .thenComparing<Duration> { it.elapsed }
    )
    private val expiredList = mutableListOf<KeysQueueElement<Key, KeyPriority>>()

    private var currentTimeQuantum: Duration

    init {
        require(initialKeys.any()) { "initialKeys must contain at least one element" }
        require(maxTimeQuantum > Duration.ZERO) { "maxTimeQuantum must be positive" }
        stopwatch.reset()
        initialKeys.forEach {
            pathSelectors[it] = basePathSelectorFactory(it)
            activeQueue.add(KeysQueueElement(it, getKeyPriority(it), Duration.ZERO))
        }
        val pathSelectorsCount = pathSelectors.size
        currentTimeQuantum = calculateTimeQuantum(pathSelectorsCount)
    }

    private fun calculateTimeQuantum(keysCount: Int): Duration =
        minOf(getRemainingTime() / keysCount, maxTimeQuantum)

    private fun peekFromQueues(): KeysQueueElement<Key, KeyPriority> {
        check(activeQueue.isNotEmpty() || expiredList.isNotEmpty()) { "Trying to peek from empty path selector" }
        if (activeQueue.isEmpty()) {
            expiredList.forEach {
                it.priority = getKeyPriority(it.key)
                activeQueue.add(it)
            }
            expiredList.clear()
            currentTimeQuantum = calculateTimeQuantum(activeQueue.size)
        }
        return activeQueue.peek()
    }

    /** Flush the final partial turn when the machine stops without another peek. */
    fun finishTimeQuantum() {
        if (!stopwatch.isRunning) return

        stopwatch.stop()
        val currentKey = activeQueue.peek() ?: return
        onTimeQuantum(currentKey.key, currentTimeQuantum, stopwatch.elapsed)
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
            finishTimeQuantum()
            activeQueue.remove(currentKey)
            if (!isCurrentPathSelectorEmpty) {
                currentKey.elapsed += stopwatch.elapsed
                expiredList.add(currentKey)
            } else {
                pathSelectors.remove(currentKey.key)
            }
            if (stopwatch.elapsed > currentTimeQuantum && activeQueue.isNotEmpty()) {
                currentTimeQuantum = if (maxTimeQuantum.isFinite()) {
                    calculateTimeQuantum(activeQueue.size)
                } else {
                    currentTimeQuantum - (stopwatch.elapsed - currentTimeQuantum) / activeQueue.size
                }
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
}
