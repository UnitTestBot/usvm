package org.usvm.regions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductRegionTest {
    @Test
    fun testSimple() {
        val r1 = SetRegion.ofSet(1, 2, 3)
        val r2 = SetRegion.ofSet(2, 3, 4)
        val r3 = SetRegion.singleton(1)
        val p1 = ProductRegion(r1, r1)
        val p2 = ProductRegion(r2, r2)
        val diff1 = p1.subtract(p2)
        assertTrue(diff1.products.size == 2)
        val p3 = ProductRegion(r3, r1)
        val p4 = ProductRegion(r1, r3)
        val diff2 = diff1.subtract(p3)
        assertTrue(diff2.products.size == 1)
        val diff3 = diff2.subtract(p4)
        assertTrue(diff3.isEmpty)
    }

    @Test
    fun testUnion() {
        val x1 = SetRegion.ofSet(1, 2)
        val x2 = SetRegion.ofSet(4, 5)

        val y1 = SetRegion.ofSet(-1, -2)
        val y2 = SetRegion.ofSet(-4, -5)

        val p1 = ProductRegion(listOf(x1 to y2, x2 to y1))
        val p2 = ProductRegion(listOf(x1 to y1, x2 to y2))

        val union = p1.union(p2)
        assertEquals(1, union.products.size)
        assertEquals(setOf(1, 2, 4, 5), union.products.first().first.toSet())
        assertEquals(setOf(-1, -2, -4, -5), union.products.first().second.toSet())
    }
}