package org.usvm.regions

import org.junit.jupiter.api.Test
import org.usvm.regions.IntervalsRegion
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RegionsTest {
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
        val tree0 = emptyRegionTree<SetRegion<Int>, Int>()
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