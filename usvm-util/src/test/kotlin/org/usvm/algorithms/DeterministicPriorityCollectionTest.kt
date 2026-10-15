package org.usvm.algorithms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class DeterministicPriorityCollectionTest {
    @Test
    fun `adding a worse priority keeps the cached minimum`() {
        val collection = DeterministicPriorityCollection<String, Int>(naturalOrder())
        collection.add(element = "first", priority = 1)
        assertEquals("first", collection.peek())

        collection.add(element = "second", priority = 2)

        assertEquals("first", collection.peek())
        collection.remove("first")
        assertEquals("second", collection.peek())
        assertEquals(1, collection.count)
    }

    @Test
    fun `adding a better priority replaces the cached minimum`() {
        val collection = DeterministicPriorityCollection<String, Int>(naturalOrder())
        collection.add(element = "second", priority = 2)
        assertEquals("second", collection.peek())

        collection.add(element = "first", priority = 1)

        assertEquals("first", collection.peek())
        collection.remove("first")
        assertEquals("second", collection.peek())
        assertEquals(1, collection.count)
    }

    @Test
    fun `adding a worse priority keeps the cached maximum with reverse order`() {
        val collection = DeterministicPriorityCollection<String, Int>(reverseOrder())
        collection.add(element = "first", priority = 2)
        assertEquals("first", collection.peek())

        collection.add(element = "second", priority = 1)

        assertEquals("first", collection.peek())
        collection.remove("first")
        assertEquals("second", collection.peek())
        assertEquals(1, collection.count)
    }

    @Test
    fun `adding a better priority replaces the cached maximum with reverse order`() {
        val collection = DeterministicPriorityCollection<String, Int>(reverseOrder())
        collection.add(element = "second", priority = 1)
        assertEquals("second", collection.peek())

        collection.add(element = "first", priority = 2)

        assertEquals("first", collection.peek())
        collection.remove("first")
        assertEquals("second", collection.peek())
        assertEquals(1, collection.count)
    }
}
