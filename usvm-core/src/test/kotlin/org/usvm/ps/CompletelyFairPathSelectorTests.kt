package org.usvm.ps

import org.junit.jupiter.api.Test
import org.usvm.util.Stopwatch
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class TestStopwatch : Stopwatch {
    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }

    override fun reset() {
        elapsedRaw = 0L
        isRunning = false
    }

    var elapsedRaw: Long = 0L

    override val elapsed: Duration
        get() = elapsedRaw.seconds

    override var isRunning: Boolean = false
        private set
}

class CompletelyFairPathSelectorTests {

    @Test
    fun fairnessTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3")
        var remainingTime = 99L
        val pathSelector = CompletelyFairPathSelector(
            sequenceOf("m1", "m2", "m3"),
            stopwatch,
            stateToMethod::getValue,
            { 1 },
            { BfsPathSelector() },
            3U
        )
        pathSelector.add(listOf("s1", "s2", "s3"))
        val times = hashMapOf("s1" to 0, "s2" to 0, "s3" to 0)
        while (remainingTime > 0) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            stopwatch.elapsedRaw++
            remainingTime--
        }
        assertEquals(33, times["s1"])
        assertEquals(33, times["s2"])
        assertEquals(33, times["s3"])
    }

    @Test
    fun oneMethodFinishedEarlyTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3")
        var remainingTime = 99L
        val pathSelector = CompletelyFairPathSelector(
            sequenceOf("m1", "m2", "m3"),
            stopwatch,
            stateToMethod::getValue,
            { 1 },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3"))
        val times = hashMapOf("s1" to 0, "s2" to 0, "s3" to 0)
        while (remainingTime > 0) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            if (peeked == "s2" && times["s2"] == 9) {
                pathSelector.remove(peeked)
            }
            stopwatch.elapsedRaw++
            remainingTime--
        }
        assertEquals(45, times["s1"])
        assertEquals(9, times["s2"])
        assertEquals(45, times["s3"])
    }

    @Test
    fun oneMethodTakesMuchTimeTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 100L
        val pathSelector = CompletelyFairPathSelector(
            sequenceOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { it },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0, "s2" to 0, "s3" to 0, "s4" to 0)
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            stopwatch.elapsedRaw++
            remainingTime--
            if (peeked == "s2") {
                stopwatch.elapsedRaw += 20L
                remainingTime -= 20L
                times[peeked] = times.getValue(peeked) + 20
            }
        }
        assertEquals(22, times["s1"])
        assertEquals(42, times["s2"])
        assertEquals(21, times["s3"])
        assertEquals(21, times["s4"])
    }

    @Test
    fun removeKeyTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = CompletelyFairPathSelector(
            sequenceOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { it },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            if (remainingTime == 35L) {
                pathSelector.removeKey("m2")
            }
            if (remainingTime == 25L) {
                pathSelector.removeKey("m4")
            }
            times[peeked] = times.getValue(peeked) + 1
            stopwatch.elapsedRaw++
            remainingTime--
        }
        assertEquals(17, times["s1"])
        assertEquals(2, times["s2"])
        assertEquals(17, times["s3"])
        assertEquals(4, times["s4"])
    }

    @Test
    fun removeKeyTest2() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = CompletelyFairPathSelector(
            sequenceOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { it },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            stopwatch.elapsedRaw++
            remainingTime--
            if (remainingTime == 36L) {
                pathSelector.removeKey("m1")
            }
        }
        assertEquals(1, times["s1"])
        assertEquals(13, times["s2"])
        assertEquals(13, times["s3"])
        assertEquals(13, times["s4"])
    }

    @Test
    fun oneTaskIsBulkButAnotherIsRemovedTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = CompletelyFairPathSelector(
            sequenceOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { it },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        while (remainingTime > 0L) {
            if (remainingTime < 30L) {
                pathSelector.removeKey("m4")
            }
            val peeked = pathSelector.peek()
            val time =
                when (peeked) {
                    "s2" -> 20L
                    else -> 1L
                }
            times[peeked] = times.getValue(peeked) + time
            stopwatch.elapsedRaw += time
            remainingTime -= time
        }

        assertEquals(10, times["s1"])
        assertEquals(20, times["s2"])
        assertEquals(10, times["s3"])
        assertEquals(0, times["s4"])
    }
}
