package org.usvm.algorithms

import kotlin.test.Test
import kotlin.test.assertEquals

class InfiniteCartesianProductTests {
    @Test
    fun testInfiniteListProduct() {
        val result = listCartesianProduct(listOf(
            sequence { var x = 0; while (true) {yield(x++)} },
            sequence { var x = 100; while (true) {yield(x++)} },
            sequence { var x = 1000; while (true) {yield(x++)} },
        )).take(40).toSet()
        assertEquals(40, result.size)
    }

    @Test
    fun testFiniteListProduct() {
        val result = listCartesianProduct(listOf(
            sequenceOf(11, 12, 13),
            sequenceOf(21, 22, 23),
            sequenceOf(31, 32, 33, 34),
        )).toSet()
        assertEquals(3 * 3 * 4, result.size)
    }

    @Test
    fun testInfiniteMapProduct() {
        val result = mapCartesianProduct(mapOf(
            1 to sequence { var x = 0; while (true) {yield(x++)} },
            2 to sequence { var x = 100; while (true) {yield(x++)} },
            3 to sequence { var x = 1000; while (true) {yield(x++)} },
        )).take(40).toSet()
        assertEquals(40, result.size)
    }

    @Test
    fun testFiniteMapProduct() {
        val result = mapCartesianProduct(mapOf(
            1 to sequenceOf(11, 12, 13),
            2 to sequenceOf(21, 22, 23),
            3 to sequenceOf(31, 32, 33, 34),
        )).toSet()
        assertEquals(3 * 3 * 4, result.size)
    }

    @Test
    fun ololo() {
        var usedElements = HashSet<Int>()
        var sequence = sequenceOf(1,2,1,2,3,1,2,3,4,1,2,3,4,5,1,2,3,4,5,6)
        sequence.filterNot {usedElements.contains(it)}.any {
            usedElements.add(it)
            println(it)
            false
        }
    }
}