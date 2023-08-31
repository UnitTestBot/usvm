package org.usvm.regions

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
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

    @Test
    fun intervalRegionTest() {
        val zero = IntervalsRegion.singleton(0)
        val two = IntervalsRegion.singleton(2)
        val three = IntervalsRegion.singleton(3)
        val ten = IntervalsRegion.singleton(10)
        val seg0_10 = IntervalsRegion.closed(0, 10)                               // [0..10]
        val seg0_10_open = seg0_10.subtract(zero).subtract(ten)             // (0..10)
        val seg2_3 = IntervalsRegion.closed(2, 3)                                 // [2..3]
        val result1 = seg0_10_open.subtract(seg2_3)                         // (0..2) U (3..10)
        val seg0_2 = IntervalsRegion.closed(0, 2).subtract(zero).subtract(two)    // (0..2)
        val seg3_10 = IntervalsRegion.closed(3, 10).subtract(three).subtract(ten) // (3..10)
        val result2 = seg3_10.union(seg0_2)

        assertEquals(result1, result2)
        assertEquals(RegionComparisonResult.INCLUDES, result1.compare(result2))
        assertEquals(result1, result1.intersect(result2))

        val seg2_15 = IntervalsRegion.closed(2, 15)
        assertEquals(RegionComparisonResult.INTERSECTS, result1.compare(seg2_15))
        assertEquals(seg3_10, result1.intersect(seg2_15))
        assertEquals(RegionComparisonResult.DISJOINT, result1.compare(two))
        assertEquals(RegionComparisonResult.DISJOINT, result1.compare(two.union(zero)))
        assertEquals(RegionComparisonResult.INCLUDES, seg0_10.compare(result1))
        assertEquals(RegionComparisonResult.INCLUDES, result1.compare(result1))

        val seg2_4 = IntervalsRegion.closed(2, 4)
        val seg3_5 = IntervalsRegion.closed(3, 5)
        assertEquals(RegionComparisonResult.INTERSECTS, seg3_5.compare(seg0_10.subtract(seg2_4)))

        val seg10_11 = IntervalsRegion.closed(10, 11).subtract(ten)
        assertEquals("(0..10) U (10..11]", seg0_10_open.union(seg10_11).toString())
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