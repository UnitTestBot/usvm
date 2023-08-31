package org.usvm.regions

import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random
import kotlin.test.assertEquals

class IntervalsRegionTest {
    val random = Random
    val ranges = generateIntegerRanges(universumLength = 20, count = 128, random)

    private fun generateIntegerRanges(universumLength: Int, count: Int, random: Random): List<IntRange> =
        List(count) {
            var (left, right) = random.nextInt(universumLength) to random.nextInt(universumLength)
            if (left > right) {
                left = right.also { right = left }
            }
            left..right
        }

    @RepeatedTest(50)
    fun testIntersection() {
        val sequence = List(100) {
            @Suppress("UNCHECKED_CAST")
            Region<Region<*>>::intersect as (Region<*>, Region<*>) -> Region<*>
        }
        testOnSequentialOperations(sequence)
    }

    @RepeatedTest(50)
    fun testSubtract() {
        val sequence = List(100) {
            @Suppress("UNCHECKED_CAST")
            Region<Region<*>>::subtract as (Region<*>, Region<*>) -> Region<*>
        }
        testOnSequentialOperations(sequence)
    }

    @RepeatedTest(50)
    fun testAll() {
        val operations = listOf(Region<Region<*>>::intersect, Region<Region<*>>::subtract, Region<Region<*>>::union)
        val sequence = List(100) {
            @Suppress("UNCHECKED_CAST")
            operations.random() as (Region<*>, Region<*>) -> Region<*>
        }
        testOnSequentialOperations(sequence)
    }


    private fun testOnSequentialOperations(operations: List<(Region<*>, Region<*>) -> Region<*>>) {
        var (intervals, set) = ranges.random().let { it.asIntervalsRegion to it.asSetRegion }
        checkRegionsEqual(intervals, set)

        for (operation in operations) {
            val range = ranges.random(random)

            val rangeAsIntervalsRegion = range.asIntervalsRegion
            val rangeAsSetRegion = range.asSetRegion

            val compareResultIntervals = intervals.compare(rangeAsIntervalsRegion)
            @Suppress("UNCHECKED_CAST")
            val newIntervals = operation(intervals, rangeAsIntervalsRegion) as IntervalsRegion<Int>

            val compareResultSet = set.compare(rangeAsSetRegion)
            @Suppress("UNCHECKED_CAST")
            val newSet = operation(set, rangeAsSetRegion) as SetRegion<Int>
            checkRegionsEqual(newIntervals, newSet)

            assertEquals(compareResultSet, compareResultIntervals, "Range: $range\n\nSet: $set\n\nIntervals: $intervals\n\n$operation")
            intervals = newIntervals
            set = newSet
        }
    }

    private fun checkRegionsEqual(intervalsRegion: IntervalsRegion<Int>, setRegion: SetRegion<Int>) {
        intervalsRegion.checkInvariants()

        val intervalsPoints = mutableListOf<Int>()
        for ((start, end) in intervalsRegion.chunked(2)) {
            val startInclusive = if (start.sort == Endpoint.Sort.CLOSED_LEFT) start.elem else start.elem + 1
            val endInclusive = if (end.sort == Endpoint.Sort.CLOSED_RIGHT) end.elem else end.elem - 1
            intervalsPoints += startInclusive..endInclusive
        }

        val setPoints = setRegion.toSet()

        assertEquals(intervalsPoints.toSet(), setPoints, "$intervalsRegion\n\t!=\n$setRegion")
    }

    val IntRange.asIntervalsRegion get() = IntervalsRegion.closed(start, endInclusive)
    val IntRange.asSetRegion get() = SetRegion.ofSequence(toList().asSequence())
}