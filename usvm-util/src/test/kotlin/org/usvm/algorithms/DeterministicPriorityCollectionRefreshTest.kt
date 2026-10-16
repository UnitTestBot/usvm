package org.usvm.algorithms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class DeterministicPriorityCollectionRefreshTest {
    @Test
    fun `refresh reorders every element including the cached selection`() {
        for (comparator in listOf(naturalOrder<Int>(), reverseOrder<Int>())) {
            val collection = DeterministicPriorityCollection<String, Int>(comparator)
            collection.add(element = "first", priority = 1)
            collection.add(element = "second", priority = 2)
            collection.add(element = "third", priority = 3)
            collection.peek()
            val evaluations = mutableListOf<String>()
            val priorities = mapOf("first" to 3, "second" to 1, "third" to 2)

            collection.updatePriorities { element ->
                evaluations.add(element)
                priorities.getValue(element)
            }

            assertEquals(expected = 3, actual = collection.count)
            assertEquals(expected = 3, actual = evaluations.size)
            assertEquals(expected = priorities.keys, actual = evaluations.toSet())
            val expected = priorities.keys.sortedWith { left, right ->
                comparator.compare(priorities.getValue(left), priorities.getValue(right))
            }
            assertEquals(expected = expected, actual = drain(collection))
        }
    }

    @Test
    fun `heap rebuilding uses a linear number of comparisons`() {
        var comparisons = 0
        val comparator = Comparator<Int> { left, right ->
            comparisons++
            left.compareTo(right)
        }
        val collection = DeterministicPriorityCollection<Int, Int>(comparator)
        val size = 1024
        repeat(size) { element -> collection.add(element = element, priority = element) }
        collection.peek()
        comparisons = 0

        collection.updatePriorities { element -> size - element }

        assertTrue(comparisons <= 2 * size, "Heapify used $comparisons comparisons for $size elements")
        assertEquals(expected = (0 until size).reversed().toList(), actual = drain(collection))
    }

    @Test
    fun `equal refreshed priorities retain every element`() {
        val collection = DeterministicPriorityCollection<Int, Int>(naturalOrder())
        repeat(8) { element -> collection.add(element = element, priority = element) }
        collection.peek()

        collection.updatePriorities { 0 }

        val elements = drain(collection)
        assertEquals(expected = 8, actual = elements.size)
        assertEquals(expected = (0 until 8).toSet(), actual = elements.toSet())
    }

    @Test
    fun `failed refresh leaves old ordering and all elements intact`() {
        val collection = DeterministicPriorityCollection<String, Int>(naturalOrder())
        collection.add(element = "first", priority = 1)
        collection.add(element = "second", priority = 2)
        collection.peek()

        assertFailsWith<IllegalStateException> {
            collection.updatePriorities { element ->
                check(element != "second")
                100
            }
        }

        assertEquals(expected = "first", actual = collection.peek())
        assertEquals(expected = listOf("first", "second"), actual = drain(collection))
    }

    @Test
    fun `empty and singleton collections can be refreshed repeatedly`() {
        val collection = DeterministicPriorityCollection<String, Int>(naturalOrder())

        collection.updatePriorities { error("Empty collection") }
        collection.add(element = "only", priority = 1)
        collection.peek()
        repeat(2) { collection.updatePriorities { 2 } }

        assertEquals(expected = listOf("only"), actual = drain(collection))
        collection.updatePriorities { error("Empty collection") }
        assertEquals(expected = 0, actual = collection.count)
    }

    private fun <T, Priority> drain(collection: DeterministicPriorityCollection<T, Priority>): List<T> {
        val elements = mutableListOf<T>()
        while (collection.count > 0) {
            val element = collection.peek()
            collection.remove(element)
            elements.add(element)
        }
        return elements
    }
}
