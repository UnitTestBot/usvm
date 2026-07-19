package org.usvm.ts.pbt.hybrid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TargetShardPlannerTest {
    private val linearTopology = MethodTopology.fromPredecessors(
        listOf(
            emptySet(),
            setOf(0),
            setOf(1),
            setOf(2),
            setOf(3),
            setOf(4),
            setOf(5),
            setOf(6),
            setOf(7),
            setOf(8),
        ),
    )

    @Test
    fun `layouts for one four eight and all are deterministic`() {
        val targets = (0 until 10).map { index -> target("t$index", index) }
        val shuffled = targets.shuffled(kotlin.random.Random(42))

        val expectedSizes = mapOf(
            TargetShardSize.ONE to listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            TargetShardSize.FOUR to listOf(4, 4, 2),
            TargetShardSize.EIGHT to listOf(8, 2),
            TargetShardSize.ALL to listOf(10),
        )
        expectedSizes.forEach { (size, layout) ->
            val orderedPlan = planTargetLayout(targets, linearTopology, size)
            val shuffledPlan = planTargetLayout(shuffled, linearTopology, size)
            assertEquals(layout, orderedPlan.map(List<PlanningTarget<String>>::size), size.flagValue)
            assertEquals(
                orderedPlan.map { shard -> shard.map(PlanningTarget<String>::targetId) },
                shuffledPlan.map { shard -> shard.map(PlanningTarget<String>::targetId) },
                size.flagValue,
            )
            assertEquals(
                orderedPlan.indices.map { stableTargetShardId("method", it, size) },
                shuffledPlan.indices.map { stableTargetShardId("method", it, size) },
                "shard IDs for ${size.flagValue}",
            )
        }
    }

    @Test
    fun `dominator proximity groups targets from the same cfg region`() {
        // 0 forks into two regions. Numeric statement order deliberately interleaves them:
        // 0 -> 1 -> 3 -> 5 and 0 -> 2 -> 4 -> 6.
        val topology = MethodTopology.fromPredecessors(
            listOf(
                emptySet(),
                setOf(0),
                setOf(0),
                setOf(1),
                setOf(2),
                setOf(3),
                setOf(4),
            ),
        )
        val targets = listOf(target("left-a", 3), target("right-a", 4), target("left-b", 5), target("right-b", 6))

        val plan = planTargetLayout(targets, topology, TargetShardSize.FOUR)

        assertEquals(listOf("left-a", "left-b", "right-a", "right-b"), plan.single().map { it.targetId })
    }

    @Test
    fun `duplicate target IDs are rejected instead of disappearing silently`() {
        val duplicate = target("same", 2)
        val error = assertFailsWith<IllegalArgumentException> {
            planTargetLayout(
                listOf(duplicate, duplicate.copy(value = "second-copy"), target("other", 4)),
                linearTopology,
                TargetShardSize.ONE,
            )
        }
        assertTrue(error.message.orEmpty().contains("duplicate target IDs"))
    }

    @Test
    fun `valid layouts never contain empty shards`() {
        val plan = planTargetLayout(
            listOf(target("same", 2), target("other", 4)),
            linearTopology,
            TargetShardSize.ONE,
        )

        assertEquals(listOf(1, 1), plan.map { it.size })
        assertTrue(plan.none { it.isEmpty() })
    }

    @Test
    fun `method boundaries and flag values are validated`() {
        assertFailsWith<IllegalArgumentException> {
            planTargetLayout(
                listOf(target("a", 1), target("b", 2).copy(methodId = "other")),
                linearTopology,
                TargetShardSize.FOUR,
            )
        }
        assertEquals(TargetShardSize.ONE, TargetShardSize.parse("1"))
        assertEquals(TargetShardSize.FOUR, TargetShardSize.parse("4"))
        assertEquals(TargetShardSize.EIGHT, TargetShardSize.parse("8"))
        assertEquals(TargetShardSize.ALL, TargetShardSize.parse("all"))
        assertFailsWith<IllegalArgumentException> { TargetShardSize.parse("2") }
    }

    private fun target(id: String, statementIndex: Int): PlanningTarget<String> = PlanningTarget(
        value = id,
        methodId = "method",
        targetId = id,
        statementIndex = statementIndex,
    )
}
