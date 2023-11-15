package org.usvm.ps

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ConstantTimeFairPathSelectorTests {

    private val constantPriority = 1

    @Test
    fun fairnessTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3")
        var remainingTime = 99L
        val pathSelector = ConstantTimeFairPathSelector(
            setOf("m1", "m2", "m3"),
            stopwatch,
            { remainingTime.seconds },
            stateToMethod::getValue,
            { constantPriority },
            { BfsPathSelector() }
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
        val pathSelector = ConstantTimeFairPathSelector(
            setOf("m1", "m2", "m3"),
            stopwatch,
            { remainingTime.seconds },
            stateToMethod::getValue,
            { constantPriority },
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
        val pathSelector = ConstantTimeFairPathSelector(
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            { remainingTime.seconds },
            stateToMethod::getValue,
            { it },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0, "s2" to 0, "s3" to 0, "s4" to 0)
        val timeSpentOnS2 = 20
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            stopwatch.elapsedRaw++
            remainingTime--
            times[peeked] = times.getValue(peeked) + 1
            if (peeked == "s2") {
                stopwatch.elapsedRaw += timeSpentOnS2
                remainingTime -= timeSpentOnS2
                times[peeked] = times.getValue(peeked) + timeSpentOnS2
            }
        }
        assertEquals(25, times["s1"])
        assertEquals(42, times["s2"])
        assertEquals(17, times["s3"])
        assertEquals(16, times["s4"])
    }

    @Test
    fun removeKeyTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = ConstantTimeFairPathSelector(
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            { remainingTime.seconds },
            stateToMethod::getValue,
            { it },
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
        assertEquals(20, times["s1"])
        assertEquals(0, times["s2"])
        assertEquals(20, times["s3"])
        assertEquals(0, times["s4"])
    }

    @Test
    fun currentKeyIsRemovedTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = ConstantTimeFairPathSelector(
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            { remainingTime.seconds },
            stateToMethod::getValue,
            { it },
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
        assertEquals(4, times["s1"])
        assertEquals(12, times["s2"])
        assertEquals(12, times["s3"])
        assertEquals(12, times["s4"])
    }

    @Test
    fun oneTaskIsBulkButAnotherIsRemovedTest() {
        val stopwatch = TestStopwatch()
        val stateToMethod = mapOf("s1" to "m1", "s2" to "m2", "s3" to "m3", "s4" to "m4")
        var remainingTime = 40L
        val pathSelector = ConstantTimeFairPathSelector(
            setOf("m1", "m2", "m3", "m4"),
            stopwatch,
            { remainingTime.seconds },
            stateToMethod::getValue,
            { it },
            { BfsPathSelector() }
        )
        pathSelector.add(listOf("s1", "s2", "s3", "s4"))
        val times = hashMapOf("s1" to 0L, "s2" to 0L, "s3" to 0L, "s4" to 0L)
        val timeToRemoveM4 = 30L
        val timeSpentOnS2 = 20L
        val timeSpentOnOtherStates = 1L
        while (remainingTime > 0L) {
            val peeked = pathSelector.peek()
            if (remainingTime == timeToRemoveM4) {
                pathSelector.removeKey("m4")
            }
            val time =
                when (peeked) {
                    "s2" -> timeSpentOnS2
                    else -> timeSpentOnOtherStates
                }
            times[peeked] = times.getValue(peeked) + time
            stopwatch.elapsedRaw += time
            remainingTime -= time
        }

        assertEquals(12, times["s1"])
        assertEquals(40, times["s2"])
        assertEquals(7, times["s3"])
        assertEquals(0, times["s4"])
    }
}
