package org.usvm.statistics

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalCoverageTargetsTest {
    @Test
    fun `coverage only changes registered goals and repeated reports are idempotent`() {
        val targets = ExternalCoverageTargets(targets = listOf("first", "second"))

        assertFalse(targets.markCovered(targets = listOf("unknown")))
        assertEquals(expected = 0L, actual = targets.revision)
        assertTrue(targets.markCovered(targets = listOf("first")))
        assertFalse(targets.markCovered(targets = listOf("first")))

        assertEquals(expected = setOf("second"), actual = targets.uncoveredTargets)
        assertEquals(expected = 1L, actual = targets.revision)
    }

    @Test
    fun `new goals can be registered without resurrecting covered goals`() {
        val targets = ExternalCoverageTargets(targets = listOf("first"))
        targets.markCovered(targets = listOf("first"))

        assertTrue(targets.addTargets(targets = listOf("first", "second", "second")))
        assertFalse(targets.addTargets(targets = listOf("first", "second")))

        assertEquals(expected = setOf("second"), actual = targets.uncoveredTargets)
        assertEquals(expected = 2L, actual = targets.revision)
    }

    @Test
    fun `goal snapshots and constructor inputs do not share mutable storage`() {
        val initial = mutableListOf("first", "second")
        val targets = ExternalCoverageTargets(targets = initial)
        val snapshot = targets.uncoveredTargets

        initial.clear()
        targets.markCovered(targets = listOf("first", "second"))

        assertEquals(expected = setOf("first", "second"), actual = snapshot)
        assertTrue(targets.uncoveredTargets.isEmpty())
        assertEquals(expected = 1L, actual = targets.revision)
    }
}
