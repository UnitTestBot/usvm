package org.usvm.regions

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class IntIntervalsRegionTest {
    private val random = Random(42)
    private val ranges = generateIntegerRanges(universumLength = 20, count = 128, random)

    @Test
    fun intervalRegionTest() {
        val zero = IntIntervalsRegion.point(0)
        val two = IntIntervalsRegion.point(2)
        val three = IntIntervalsRegion.point(3)
        val ten = IntIntervalsRegion.point(10)

        val seg10 = IntIntervalsRegion.ofHalfOpen(0, 10)
        val interval = seg10
            .subtract(zero)
            .subtract(two)
            .subtract(three)
            .union(ten)

        interval.checkInvariants()

        val result = mutableListOf<Int>()

        for ((leftInclusive, rightExclusive) in interval.chunked(2)) {
            result += leftInclusive until rightExclusive
        }
        assertEquals(listOf(1, 4, 5, 6, 7, 8, 9, 10), result)
    }

    @Test
    fun testIntersection() {
        repeat(50) {
            val sequence = List(100) {
                @Suppress("UNCHECKED_CAST")
                Region<Region<*>>::intersect as (Region<*>, Region<*>) -> Region<*>
            }
            testOnSequentialOperations(sequence)
        }
    }

    @Test
    fun testSubtract() {
        repeat(50) {
            val sequence = List(100) {
                @Suppress("UNCHECKED_CAST")
                Region<Region<*>>::subtract as (Region<*>, Region<*>) -> Region<*>
            }
            testOnSequentialOperations(sequence)
        }
    }

    @Test
    fun testAll() {
        repeat(50) {
            val operations = listOf(Region<Region<*>>::intersect, Region<Region<*>>::subtract, Region<Region<*>>::union)
            val sequence = List(100) {
                @Suppress("UNCHECKED_CAST")
                operations.random(random) as (Region<*>, Region<*>) -> Region<*>
            }
            testOnSequentialOperations(sequence)
        }
    }


    private fun testOnSequentialOperations(operations: List<(Region<*>, Region<*>) -> Region<*>>) {
        var (intervals, set) = ranges.random(random).let { it.asIntervalsRegion to it.asSetRegion }
        checkRegionsEqual(intervals, set)

        for (operation in operations) {
            val range = ranges.random(random)

            val rangeAsIntervalsRegion = range.asIntervalsRegion
            val rangeAsSetRegion = range.asSetRegion

            val compareResultIntervals = intervals.compare(rangeAsIntervalsRegion)
            val newIntervals = operation(intervals, rangeAsIntervalsRegion) as IntIntervalsRegion

            val compareResultSet = set.compare(rangeAsSetRegion)

            @Suppress("UNCHECKED_CAST")
            val newSet = operation(set, rangeAsSetRegion) as SetRegion<Int>
            checkRegionsEqual(newIntervals, newSet)

            assertEquals(
                compareResultSet,
                compareResultIntervals,
                "Range: $range\n\nSet: $set\n\nIntervals: $intervals\n\n$operation"
            )
            intervals = newIntervals
            set = newSet
        }
    }

    private fun checkRegionsEqual(intervalsRegion: IntIntervalsRegion, setRegion: SetRegion<Int>) {
        intervalsRegion.checkInvariants()

        val setPoints = setRegion.toSet()

        val intervalsPoints = mutableListOf<Int>()
        for ((start, end) in intervalsRegion.chunked(2)) {
            intervalsPoints += start until end
        }
        if (intervalsRegion.intMaxIncluded) {
            intervalsPoints += Int.MAX_VALUE
        }

        assertEquals(setPoints, intervalsPoints.toSet(), "$intervalsRegion\n\t!=\n$setRegion")
    }

    private val IntRange.asIntervalsRegion get() = IntIntervalsRegion.ofClosed(start, endInclusive)
    private val IntRange.asSetRegion get() = SetRegion.ofSequence(toList().asSequence())

    private fun generateIntegerRanges(
        universumLength: Int,
        count: Int, random: Random,
    ): List<IntRange> =
        List(count) {
            var (left, right) = random.nextInt(universumLength) to random.nextInt(universumLength)
            if (left > right) {
                left = right.also { right = left }
            }
            left..right
        } + List(count) {
            val left = Int.MAX_VALUE - random.nextInt(universumLength)
            left..Int.MAX_VALUE
        }
}
