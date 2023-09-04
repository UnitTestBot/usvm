package org.usvm.regions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionTreeIteratorTest {
    @Test
    fun testSimpleRecursiveIterator() {
        val region = SetRegion.ofSet(0, 1, 2, 3, 4, 5)
        val tree = emptyRegionTree<SetRegion<Int>, Int>()
            .write(region, 10)                // {0..5} -> 10
            .write(SetRegion.singleton(3), 5) // {0..2, 4, 5} -> 10, {3} -> (5, {3} -> 10)

        val treeIterator = tree.iterator()

        val (firstKey, firstRegion) = treeIterator.next()   // {0..2, 4, 5} -> 10
        val (secondKey, secondRegion) = treeIterator.next() // {3} -> 10
        val (thirdKey, thirdRegion) = treeIterator.next()   // {3} -> 5

        assertTrue { firstKey == 10 && firstRegion == SetRegion.ofSet(0, 1, 2, 4, 5) }
        assertTrue { secondKey == 10 && secondRegion == SetRegion.singleton(3) }
        assertTrue { thirdKey == 5 && thirdRegion == SetRegion.singleton(3) }

        assertFalse { treeIterator.hasNext() }
        assertThrows<NoSuchElementException> { treeIterator.next() }
    }

    @Test
    fun testRecursiveIteratorWritingsInTheSameRegion() {
        val region = SetRegion.singleton(1)
        val writes = List(5) { it }
        val tree = writes.fold(emptyRegionTree<SetRegion<Int>, Int>()) { acc, i ->
            acc.write(region, i)
        }

        val iterator = tree.iterator()

        writes.forEach {
            val (key, currentRegion) = iterator.next()
            assertTrue { key == it && currentRegion == region }
        }

        assertFalse { iterator.hasNext() }
        assertThrows<NoSuchElementException> { iterator.next() }
    }


    @Test
    fun testRecursiveIteratorComplicatedRegion() {
        val tree = constructComplicatedTree()

        val nodes = tree.map { it }
        val expectedValues = listOf(
            1 to SetRegion.ofSet(7, 8, 9, 10),
            1 to SetRegion.ofSet(1, 6),
            1 to SetRegion.ofSet(5),
            1 to SetRegion.ofSet(4),
            2 to SetRegion.ofSet(4),
            3 to SetRegion.ofSet(4, 5),
            4 to SetRegion.ofSet(4, 5),
            5 to SetRegion.ofSet(1, 4, 5, 6),
            1 to SetRegion.singleton(0),
            6 to SetRegion.singleton(0),
            1 to SetRegion.singleton(2),
            2 to SetRegion.singleton(2),
            5 to SetRegion.singleton(2),
            7 to SetRegion.singleton(2),
            1 to SetRegion.singleton(3),
            2 to SetRegion.singleton(3),
            3 to SetRegion.singleton(3),
            5 to SetRegion.singleton(3),
            8 to SetRegion.singleton(3),
        )

        nodes.zip(expectedValues).forEach {
            assertTrue { it.first == it.second }
        }

        assertThrows<NoSuchElementException> {
            tree.iterator().let { iterator ->
                repeat(20) { iterator.next() }
            }
        }
    }

    @Test
    fun test() {
        val tree = emptyRegionTree<SetRegion<Int>, Int>()
            .write(SetRegion.ofSet(1, 2), 10)  // {1, 2} -> 10
            .write(SetRegion.singleton(1), 1)  // {2} -> 10, {1} -> (1, {1} -> 10)
            .write(SetRegion.singleton(2), 2)  // {1} -> (1, {1} -> 10), {2} -> (2, {2} -> 10)

        val values = tree.map { it }
        val expectedValues = listOf(
            10 to SetRegion.singleton(1),
            1 to SetRegion.singleton(1),
            10 to SetRegion.singleton(2),
            2 to SetRegion.singleton(2)
        )

        values.zip(expectedValues).forEach {
            assertTrue { it.first == it.second }
        }
    }

    /*
        {7, 8, 9, 10} -> 1:
            emptyTree
        {1, 4, 5, 6} -> 5:
            {1, 6} -> 1:
                emptyTree
            {4, 5} -> 4:
                {4, 5} -> 3:
                    {5} -> 1:
                        emptyTree
                    {4} -> 2:
                        {4} -> 1:
                            emptyTree
        {0} -> 6:
            {0} -> 1:
                emptyTree
        {2} -> 7:
            {2} -> 5:
                {2} -> 2:
                    {2} -> 1:
                        emptyTree
        {3} -> 8:
            {3} -> 5:
                {3} -> 3:
                    {3} -> 2:
                        {3} -> 1:
                            emptyTree
     */
    private fun constructComplicatedTree(): RegionTree<SetRegion<Int>, Int> {
        val initialRegion = SetRegion.ofSet(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val firstRegion = SetRegion.ofSet(2, 3, 4)
        val secondRegion = SetRegion.ofSet(3, 4, 5)
        val thirdRegion = SetRegion.ofSet(4, 5)
        val fourthRegion = SetRegion.ofSet(1, 2, 3, 4, 5, 6)

        val firstPoint = SetRegion.singleton(0)
        val secondPoint = SetRegion.singleton(2)
        val thirdPoint = SetRegion.singleton(3)

        return emptyRegionTree<SetRegion<Int>, Int>()
            .write(initialRegion, 1)
            .write(firstRegion, 2)
            .write(secondRegion, 3)
            .write(thirdRegion, 4)
            .write(fourthRegion, 5)
            .write(firstPoint, 6)
            .write(secondPoint, 7)
            .write(thirdPoint, 8)
    }
}