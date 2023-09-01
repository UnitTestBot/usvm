package org.usvm.regions

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class IntervalsRegionTest {
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
        assertEquals(Region.ComparisonResult.INCLUDES, result1.compare(result2))
        assertEquals(result1, result1.intersect(result2))

        val seg2_15 = IntervalsRegion.closed(2, 15)
        assertEquals(Region.ComparisonResult.INTERSECTS, result1.compare(seg2_15))
        assertEquals(seg3_10, result1.intersect(seg2_15))
        assertEquals(Region.ComparisonResult.DISJOINT, result1.compare(two))
        assertEquals(Region.ComparisonResult.DISJOINT, result1.compare(two.union(zero)))
        assertEquals(Region.ComparisonResult.INCLUDES, seg0_10.compare(result1))
        assertEquals(Region.ComparisonResult.INCLUDES, result1.compare(result1))

        val seg2_4 = IntervalsRegion.closed(2, 4)
        val seg3_5 = IntervalsRegion.closed(3, 5)
        assertEquals(Region.ComparisonResult.INTERSECTS, seg3_5.compare(seg0_10.subtract(seg2_4)))

        val seg10_11 = IntervalsRegion.closed(10, 11).subtract(ten)
        assertEquals("(0..10) U (10..11]", seg0_10_open.union(seg10_11).toString())
    }


}