package org.usvm.regions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetRegionTest {
    @Test
    fun testSimple() {
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

    @Test
    fun testUniverseUnion() {
        val universe = SetRegion.universe<Int>()
        val union = universe.union(universe)
        assertEquals(Region.ComparisonResult.INCLUDES, universe.compare(union))
        assertEquals(Region.ComparisonResult.INCLUDES, union.compare(universe))
    }

    @Test
    fun testSimpleUnion() {
        val a = SetRegion.singleton(1)
        val b = SetRegion.singleton(2)
        val c = SetRegion.singleton(3)
        val union = a.union(b).union(c)
        val abc = SetRegion.ofSet(1, 2, 3)

        assertTrue(abc.subtract(union).isEmpty)
        assertTrue(union.subtract(abc).isEmpty)
    }

    @Test
    fun testUnionAndIntersect() {
        val universe = SetRegion.universe<Int>()

        val abc = SetRegion.ofSet(1, 2, 3)
        val def = SetRegion.ofSet(4, 5, 6)

        val universeNoAbc = universe.subtract(abc) // Z\{1, 2, 3}
        val universeNoDef = universe.subtract(def) // Z\{4, 5, 6}

        val intersection = universeNoAbc.intersect(universeNoDef) // Z\{1, 2, 3, 4, 5, 6}
        val union = abc.union(def) // {1, 2, 3, 4, 5, 6}
        val all = intersection.union(union) // Z

        assertTrue(universe.subtract(all).isEmpty)
        assertTrue(all.subtract(universe).isEmpty)
    }
}