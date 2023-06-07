package org.usvm.test

import org.junit.jupiter.api.Test
import org.usvm.util.cached
import kotlin.test.assertEquals

class CachingSequenceTest {
    @Test
    fun `Test consumes sequence exactly once`() {
        var callCounter = 0
        val sequence = Sequence {
            callCounter++
            listOf(1, 2, 3).iterator()
        }

        val cachingSequence = sequence.cached()

        val prefix1 = cachingSequence.take(1).toList()
        assertEquals(listOf(1), prefix1)

        val prefix2 = cachingSequence.take(2).toList()
        assertEquals(listOf(1, 2), prefix2)

        val prefix3 = cachingSequence.take(3).toList()
        assertEquals(listOf(1, 2, 3), prefix3)

        assertEquals(1, callCounter)
    }

    @Test
    fun `Test filters sequence`() {
        var callCounter = 0
        val sequence = Sequence {
            callCounter++
            listOf(1, 2, 3).iterator()
        }

        val cachingSequence = sequence.cached()

        val prefix3 = cachingSequence.take(3).toList()
        assertEquals(listOf(1, 2, 3), prefix3)


        val filtered = cachingSequence.filter { it % 2 == 1 }.toList()

        assertEquals(listOf(1, 3), filtered)

        assertEquals(1, callCounter)
    }
}