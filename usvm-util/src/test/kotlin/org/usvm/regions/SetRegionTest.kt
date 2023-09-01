package org.usvm.regions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SetRegionTest {
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
        assertEquals(Region.ComparisonResult.DISJOINT, no01.compare(zero))
        assertEquals(Region.ComparisonResult.DISJOINT, one.compare(zero))
        assertEquals(Region.ComparisonResult.INCLUDES, no0.compare(two))
        assertEquals(Region.ComparisonResult.INTERSECTS, two.compare(no01))
        assertEquals(Region.ComparisonResult.INTERSECTS, no0.compare(no2))
        assertEquals(Region.ComparisonResult.INCLUDES, zero.compare(zero))
    }
}