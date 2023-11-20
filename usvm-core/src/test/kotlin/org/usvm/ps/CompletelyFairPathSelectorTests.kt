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
        val peeksInQuantum = 3U
        val pathSelector = CompletelyFairPathSelector(
            setOf("m1", "m2", "m3"),
            stopwatch,
            stateToMethod::getValue,
            { BfsPathSelector() },
            peeksInQuantum
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
            setOf("m1", "m2", "m3"),
            stopwatch,
            stateToMethod::getValue,
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3"))
        val times = hashMapOf("s1" to 0, "s2" to 0, "s3" to 0)
        val timeToRemoveS2 = 9
        while (remainingTime > 0) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            if (peeked == "s2" && times["s2"] == timeToRemoveS2) {
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
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0, "s2" to 0, "s3" to 0, "s4" to 0)
        val timeSpentOnS2 = 20
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            stopwatch.elapsedRaw++
            remainingTime--
            if (peeked == "s2") {
                stopwatch.elapsedRaw += timeSpentOnS2
                remainingTime -= timeSpentOnS2
                times[peeked] = times.getValue(peeked) + timeSpentOnS2
            }
        }
        assertEquals(22, times["s1"])
        assertEquals(42, times["s2"])
        assertEquals(22, times["s3"])
        assertEquals(22, times["s4"])
    }

    @Test
    fun removeKeyTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = CompletelyFairPathSelector(
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        val timeToRemoveM2 = 35L
        val timeToRemoveM4 = 25L
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            if (remainingTime == timeToRemoveM2) {
                pathSelector.removeKey("m2")
            }
            if (remainingTime == timeToRemoveM4) {
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
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        val timeToRemoveM1 = 36L
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            times[peeked] = times.getValue(peeked) + 1
            stopwatch.elapsedRaw++
            remainingTime--
            if (remainingTime == timeToRemoveM1) {
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
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            stateToMethod::getValue,
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        val timeThresholdToRemoveM4 = 30L
        val timeSpentOnS2 = 20L
        val timeSpentOnOtherStates = 1L
        while (remainingTime > 0L) {
            if (remainingTime < timeThresholdToRemoveM4) {
                pathSelector.removeKey("m4")
            }
            val peeked = pathSelector.peek()
            val time =
                when (peeked) {
                    "s2" -> timeSpentOnS2
                    else -> timeSpentOnOtherStates
                }
            times[peeked] = times.getValue(peeked) + time
            stopwatch.elapsedRaw += time
            remainingTime -= time
        }

        assertEquals(9, times["s1"])
        assertEquals(20, times["s2"])
        assertEquals(10, times["s3"])
        assertEquals(1, times["s4"])
    }
}
