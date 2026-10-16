package org.usvm.ps

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoverageGuidedPathSelectorRefreshTest {
    @Test
    fun `default interval keeps weights stale for 128 completed selections`() {
        val evaluatedTargets = mutableListOf<String>()
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("left", "right"),
            distance = { state, target ->
                evaluatedTargets.add(target)
                val matchingDistance = if (state == "left") 0u else 1u
                if (state == target) matchingDistance else 10u
            },
        )
        selector.add(states = listOf("left", "right"))
        selector.markCovered(targets = listOf("left"))

        repeat(127) {
            assertEquals(expected = "left", actual = selector.peek())
            evaluatedTargets.clear()
            selector.update(state = "left")
            assertEquals(expected = listOf("left", "right"), actual = evaluatedTargets.sorted())
        }
        evaluatedTargets.clear()
        repeat(256) {
            assertEquals(expected = "left", actual = selector.peek())
        }
        assertTrue(evaluatedTargets.isEmpty())

        selector.update(state = "left")
        evaluatedTargets.clear()

        assertEquals(expected = "right", actual = selector.peek())
        assertEquals(expected = listOf("right", "right"), actual = evaluatedTargets)
        evaluatedTargets.clear()
        selector.peek()
        assertTrue(evaluatedTargets.isEmpty())
    }

    @Test
    fun `new and updated states use the old snapshot until the interval expires`() {
        val evaluatedTargets = mutableListOf<String>()
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("old"),
            distance = { state, target ->
                evaluatedTargets.add(target)
                val matchingDistance = if (state == "left") 0u else 1u
                if (state == target) matchingDistance else 10u
            },
            priorityRefreshInterval = 2,
        )
        selector.add(states = listOf("old"))
        selector.peek()

        selector.addTargets(targets = listOf("new"))
        selector.markCovered(targets = listOf("old"))
        evaluatedTargets.clear()
        selector.add(states = listOf("new"))
        selector.update(state = "old")

        assertEquals(expected = listOf("old", "old"), actual = evaluatedTargets)
        assertEquals(expected = setOf("new"), actual = selector.uncoveredTargets)
        assertEquals(expected = "old", actual = selector.peek())

        selector.update(state = "old")
        evaluatedTargets.clear()

        assertEquals(expected = "new", actual = selector.peek())
        assertEquals(expected = listOf("new", "new"), actual = evaluatedTargets)
    }

    @Test
    fun `only one completion of the selected state advances the interval`() {
        var evaluations = 0
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("left", "right"),
            distance = { state, target ->
                evaluations++
                val matchingDistance = if (state == "left") 0u else 1u
                if (state == target) matchingDistance else 10u
            },
            priorityRefreshInterval = 2,
        )
        selector.add(states = listOf("left", "right", "other"))
        selector.peek()
        selector.markCovered(targets = listOf("left"))

        selector.update(state = "other")
        selector.remove(state = "other")
        selector.update(state = "left")
        selector.update(state = "left")
        val beforePeek = evaluations

        assertEquals(expected = "left", actual = selector.peek())
        assertEquals(expected = beforePeek, actual = evaluations)

        selector.remove(state = "left")
        val beforeRefresh = evaluations

        assertEquals(expected = "right", actual = selector.peek())
        assertEquals(expected = beforeRefresh + 1, actual = evaluations)
    }

    @Test
    fun `each refresh starts a new interval including newly registered goals`() {
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("left", "right"),
            distance = { state, target ->
                val destination = if (target == "left-again") "left" else target
                if (state == destination) 0u else 10u
            },
            priorityRefreshInterval = 2,
        )
        selector.add(states = listOf("left", "right"))
        selector.markCovered(targets = listOf("left"))
        repeat(2) { selector.update(state = selector.peek()) }
        assertEquals(expected = "right", actual = selector.peek())

        selector.addTargets(targets = listOf("left-again"))
        selector.markCovered(targets = listOf("right"))
        selector.update(state = "right")

        assertEquals(expected = "right", actual = selector.peek())

        selector.update(state = "right")

        assertEquals(expected = "left", actual = selector.peek())
        assertEquals(expected = setOf("left-again"), actual = selector.uncoveredTargets)
    }

    @Test
    fun `unchanged goals do not trigger refresh even after the interval`() {
        var evaluations = 0
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("goal"),
            distance = { _, _ ->
                evaluations++
                0u
            },
            priorityRefreshInterval = 2,
        )
        selector.add(states = listOf("first", "second"))

        repeat(8) { selector.update(state = selector.peek()) }
        val beforePeek = evaluations
        selector.markCovered(targets = listOf("unknown"))
        selector.addTargets(targets = listOf("goal"))
        selector.peek()

        assertEquals(expected = 2 + 8, actual = evaluations)
        assertEquals(expected = beforePeek, actual = evaluations)
    }

    @Test
    fun `saturation avoids distance work and later goals resume guidance`() {
        var distancesAllowed = true
        val evaluatedTargets = mutableListOf<String>()
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("old"),
            distance = { state, target ->
                check(distancesAllowed)
                evaluatedTargets.add(target)
                val matchingDistance = if (state == "left") 0u else 1u
                if (state == target) matchingDistance else 10u
            },
            priorityRefreshInterval = 2,
        )
        selector.add(states = listOf("old", "new"))
        distancesAllowed = false

        selector.markCovered(targets = listOf("old"))
        repeat(4) { selector.update(state = selector.peek()) }
        selector.add(states = listOf("later"))

        assertTrue(selector.uncoveredTargets.isEmpty())

        selector.addTargets(targets = listOf("new"))
        distancesAllowed = true
        evaluatedTargets.clear()

        assertEquals(expected = "new", actual = selector.peek())
        assertEquals(expected = listOf("new", "new", "new"), actual = evaluatedTargets)
        assertEquals(expected = setOf("old", "new", "later"), actual = selector.drain().toSet())
    }

    @Test
    fun `failed refresh preserves the snapshot and can be retried`() {
        var failRefresh = false
        val evaluatedTargets = mutableListOf<String>()
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("left", "right"),
            distance = { state, target ->
                check(!failRefresh)
                evaluatedTargets.add(target)
                val matchingDistance = if (state == "left") 0u else 1u
                if (state == target) matchingDistance else 10u
            },
            priorityRefreshInterval = 1,
        )
        selector.add(states = listOf("left", "right"))
        selector.peek()
        selector.markCovered(targets = listOf("left"))
        selector.update(state = "left")
        failRefresh = true

        assertFailsWith<IllegalStateException> { selector.peek() }

        failRefresh = false
        evaluatedTargets.clear()
        selector.add(states = listOf("other"))

        assertEquals(expected = listOf("left", "right"), actual = evaluatedTargets.sorted())
        assertEquals(expected = "right", actual = selector.peek())
        assertEquals(expected = setOf("left", "right", "other"), actual = selector.drain().toSet())
    }

    @Test
    fun `refresh interval must be positive`() {
        for (interval in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> {
                CoverageGuidedPathSelector<String, String>(
                    targets = emptyList(),
                    distance = { _, _ -> 0u },
                    priorityRefreshInterval = interval,
                )
            }
        }
    }
}
