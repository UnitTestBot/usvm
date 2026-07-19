package org.usvm.ts.pbt.hybrid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResidualShardCoordinatorTest {
    @Test
    fun `incidental replay coverage removes a later shard before launch`() {
        val covered = mutableSetOf<String>()
        val launches = mutableListOf<List<String>>()
        val plans = listOf(
            shard("s1", "a"),
            shard("s2", "b", "c"),
            shard("s3", "d"),
        )

        val executions = executeResidualShards(
            shards = plans,
            replayPruneBetweenShards = true,
            targetId = { it },
            isReplayCovered = covered::contains,
        ) { _, active ->
            assertTrue(active.isNotEmpty(), "an empty shard reached the launcher")
            launches += active
            if (active == listOf("a")) covered += setOf("a", "b", "c")
            active.size
        }

        assertEquals(listOf(listOf("a"), listOf("d")), launches)
        assertTrue(executions[0].launched)
        assertFalse(executions[1].launched)
        assertEquals(listOf("b", "c"), executions[1].replayPrunedTargets)
        assertTrue(executions[1].activeTargets.isEmpty())
        assertTrue(executions[2].launched)
    }

    @Test
    fun `coverage is re-read for fallback plans and skipped targets are not runs`() {
        val covered = mutableSetOf("fallback-a", "fallback-b")
        var machineRuns = 0

        val executions = executeResidualShards(
            shards = listOf(shard("fallback-1", "fallback-a"), shard("fallback-2", "fallback-b")),
            replayPruneBetweenShards = true,
            targetId = { it },
            isReplayCovered = covered::contains,
        ) { _, _ ->
            machineRuns++
        }

        assertEquals(0, machineRuns)
        assertEquals(2, executions.sumOf { it.replayPrunedTargets.size })
        assertTrue(executions.none { it.launched })
    }

    @Test
    fun `disabled replay pruning preserves every non-empty launch`() {
        val executions = executeResidualShards(
            shards = listOf(shard("s1", "a"), shard("s2", "b")),
            replayPruneBetweenShards = false,
            targetId = { it },
            isReplayCovered = { true },
        ) { _, active -> active.single() }

        assertEquals(listOf("a", "b"), executions.mapNotNull { it.launchResult })
        assertTrue(executions.all { it.launched })
        assertTrue(executions.all { it.replayPrunedTargets.isEmpty() })
    }

    private fun shard(id: String, vararg targets: String): ResidualShardPlan<String> =
        ResidualShardPlan(TargetShardId(id), targets.toList())
}
