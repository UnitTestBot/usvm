package org.usvm.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.usvm.util.RegionComparisonResult
import org.usvm.util.Intervals
import org.usvm.util.SetRegion
import org.usvm.util.ProductRegion
import org.usvm.util.emptyRegionTree

internal class RegionsTest {
    @Test
    fun intervalRegionTest() {
        val zero = Intervals.singleton(0)
        val two = Intervals.singleton(2)
        val three = Intervals.singleton(3)
        val ten = Intervals.singleton(10)
        val seg0_10 = Intervals.closed(0, 10)                               // [0..10]
        val seg0_10_open = seg0_10.subtract(zero).subtract(ten)             // (0..10)
        val seg2_3 = Intervals.closed(2, 3)                                 // [2..3]
        val result1 = seg0_10_open.subtract(seg2_3)                         // (0..2) U (3..10)
        val seg0_2 = Intervals.closed(0, 2).subtract(zero).subtract(two)    // (0..2)
        val seg3_10 = Intervals.closed(3, 10).subtract(three).subtract(ten) // (3..10)
        val result2 = seg3_10.union(seg0_2)

        assertEquals(result1, result2)
        assertEquals(RegionComparisonResult.INCLUDES, result1.compare(result2))
        assertEquals(result1, result1.intersect(result2))

        val seg2_15 = Intervals.closed(2, 15)
        assertEquals(RegionComparisonResult.INTERSECTS, result1.compare(seg2_15))
        assertEquals(seg3_10, result1.intersect(seg2_15))
        assertEquals(RegionComparisonResult.DISJOINT, result1.compare(two))
        assertEquals(RegionComparisonResult.DISJOINT, result1.compare(two.union(zero)))
        assertEquals(RegionComparisonResult.INCLUDES, seg0_10.compare(result1))
        assertEquals(RegionComparisonResult.INCLUDES, result1.compare(result1))

        val seg2_4 = Intervals.closed(2, 4)
        val seg3_5 = Intervals.closed(3, 5)
        assertEquals(RegionComparisonResult.INTERSECTS, seg3_5.compare(seg0_10.subtract(seg2_4)))

        val seg10_11 = Intervals.closed(10, 11).subtract(ten)
        assertEquals("(0..10) U (10..11]", seg0_10_open.union(seg10_11).toString())
    }

    @Test
    fun setRegionTest() {
        val zero = SetRegion.singleton(0)                       // {0}
        val one = SetRegion.singleton(1)                        // {1}
        val two = SetRegion.singleton(2)                        // {2}
        val universe = SetRegion.universe<Int>()                // Z
        val no0 = universe.subtract(zero)                       // Z\{0}
        val no2 = universe.subtract(two)                        // Z\{2}
        val no01 = no0.subtract(one)                            // Z\{0,1}
        val no012 = no01.subtract(two)                          // Z\{0,1,2}
        val empty = no012.subtract(no012)
        assertEquals(zero, zero.intersect(universe))            // {0} /\ Z = {0}
        assertEquals(empty, universe.subtract(universe))        // Z \ Z = empty
        assertEquals(two, no01.subtract(no012))                 // (Z\{0,1}) \ (Z\{0,1,2}) = {2}
        assertEquals(no012, no01.intersect(no2))                // (Z\{0,1}) /\ (Z\{2}) = Z\{0,1,2}
        assertEquals(RegionComparisonResult.DISJOINT, no01.compare(zero))
        assertEquals(RegionComparisonResult.DISJOINT, one.compare(zero))
        assertEquals(RegionComparisonResult.INCLUDES, no0.compare(two))
        assertEquals(RegionComparisonResult.INTERSECTS, two.compare(no01))
        assertEquals(RegionComparisonResult.INTERSECTS, no0.compare(no2))
        assertEquals(RegionComparisonResult.INCLUDES, zero.compare(zero))
    }

    @Test
    fun productRegionTest() {
        val r1 = SetRegion.ofSet(1, 2, 3)
        val r2 = SetRegion.ofSet(2, 3, 4)
        val r3 = SetRegion.singleton(1)
        val p1 = ProductRegion(r1, r1)
        val p2 = ProductRegion(r2, r2)
        val diff1 = p1.subtract(p2)
        assert(diff1.products.size == 2)
        val p3 = ProductRegion(r3, r1)
        val p4 = ProductRegion(r1, r3)
        val diff2 = diff1.subtract(p3)
        assert(diff2.products.size == 1)
        val diff3 = diff2.subtract(p4)
        assert(diff3.isEmpty)
    }

    @Test
    fun regionTreeTest() {
        val seg0_10 = SetRegion.ofSet(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val seg2_4 = SetRegion.ofSet(2, 3, 4)
        val seg3_5 = SetRegion.ofSet(3, 4, 5)
        val seg4_5 = SetRegion.ofSet(4, 5)
        val seg1_6 = SetRegion.ofSet(1, 2, 3, 4, 5, 6)
        val seg0 = SetRegion.singleton(0)
        val seg2 = SetRegion.singleton(2)
        val seg3 = SetRegion.singleton(3)
        // Test writing
        val tree0 = emptyRegionTree<Int, SetRegion<Int>>()
        val tree1 = tree0.write(seg0_10, 0)
        val tree2 = tree1.write(seg2_4, 1)
        val tree3 = tree2.write(seg3_5, 2)
        tree0.checkInvariant()
        tree1.checkInvariant()
        tree2.checkInvariant()
        tree3.checkInvariant()
        // Test reading
        val loc0 = tree3.localize(seg0)
        val loc2 = tree3.localize(seg2)
        val loc3 = tree3.localize(seg3)
        val loc4_5 = tree3.localize(seg4_5)
        val loc1_6 = tree3.localize(seg1_6)
        loc0.checkInvariant()
        loc2.checkInvariant()
        loc3.checkInvariant()
        loc4_5.checkInvariant()
        loc1_6.checkInvariant()
        assertEquals(1, loc0.entries.entries.size)
        assertEquals(1, loc2.entries.entries.size)
        assertEquals(1, loc3.entries.entries.size)
        assertEquals(1, loc4_5.entries.entries.size)
        assertEquals(3, loc1_6.entries.entries.size)
        assertEquals(0, loc0.entries.entries.first().value.first)
        assertEquals(1, loc2.entries.entries.first().value.first)
        assertEquals(2, loc3.entries.entries.first().value.first)
        assertEquals(2, loc4_5.entries.entries.first().value.first)
    }
}