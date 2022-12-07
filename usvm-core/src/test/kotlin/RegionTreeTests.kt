package org.usvm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.usvm.regions.RegionComparisonResult
import org.usvm.regions.SetRegion
import org.usvm.regions.emptyRegionTree

internal class RegionsTest {
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