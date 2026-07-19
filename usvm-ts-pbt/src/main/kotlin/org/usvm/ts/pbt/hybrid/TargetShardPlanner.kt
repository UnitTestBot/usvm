package org.usvm.ts.pbt.hybrid

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.external.stableBranchId
import org.usvm.ts.pbt.external.stableMethodId
import java.util.IdentityHashMap

private const val FOUR_TARGETS: Int = 4
private const val EIGHT_TARGETS: Int = 8

/** Supported values of the `targetShardSize` experiment flag. */
enum class TargetShardSize(
    val flagValue: String,
    internal val limit: Int?,
) {
    ONE("1", 1),
    FOUR("4", FOUR_TARGETS),
    EIGHT("8", EIGHT_TARGETS),
    ALL("all", null),
    ;

    companion object {
        fun parse(value: String): TargetShardSize = entries.singleOrNull { it.flagValue == value }
            ?: throw IllegalArgumentException("targetShardSize must be one of 1, 4, 8, all; got '$value'")
    }
}

@JvmInline
value class TargetShardId(val value: String) {
    init {
        require(value.isNotBlank()) { "target shard id must not be blank" }
    }

    override fun toString(): String = value
}

data class TargetShard(
    val id: TargetShardId,
    val method: EtsMethod,
    val targets: List<CoverageTracker.UncoveredBranch>,
) {
    init {
        require(targets.isNotEmpty()) { "target shard must not be empty" }
        require(targets.all { it.method === method }) { "target shard cannot mix entry methods" }
    }
}

/**
 * Builds deterministic, method-local target shards. Within a method, the next target is selected
 * by minimum distance to the current shard in the method's dominator tree. Stable EtsIR locations
 * break ties, so input collection order never affects the layout.
 */
class TargetShardPlanner(
    private val shardSize: TargetShardSize,
) {
    fun plan(targets: Collection<CoverageTracker.UncoveredBranch>): List<TargetShard> {
        if (targets.isEmpty()) return emptyList()

        val byMethod = targets.groupBy(CoverageTracker.UncoveredBranch::method)
        val methodIds = byMethod.keys.associateWith(::stableMethodId)
        require(methodIds.values.toSet().size == methodIds.size) {
            "stable method-id collision in target shard planner"
        }

        return byMethod.keys
            .sortedBy(methodIds::getValue)
            .flatMap { method -> planMethod(method, methodIds.getValue(method), byMethod.getValue(method)) }
    }

    private fun planMethod(
        method: EtsMethod,
        methodId: String,
        targets: List<CoverageTracker.UncoveredBranch>,
    ): List<TargetShard> {
        val topology = MethodTopology.from(method)
        val candidates = targets.map { target ->
            require(target.method === method) { "target method changed while planning shards" }
            val statementIndex = topology.indexOf(target.edge.ifStmt)
            val successorOrdinal = method.cfg.successors(target.edge.ifStmt)
                .toList()
                .indexOfFirst { it === target.edge.successor }
            require(successorOrdinal >= 0) { "target edge successor is outside its method CFG" }
            PlanningTarget(
                value = target,
                methodId = methodId,
                targetId = stableBranchId(method, target.edge.ifStmt, target.edge.successor),
                statementIndex = statementIndex,
                successorOrdinal = successorOrdinal,
            )
        }

        return planTargetLayout(candidates, topology, shardSize).mapIndexed { index, shard ->
            TargetShard(
                id = stableTargetShardId(methodId, index, shardSize),
                method = method,
                targets = shard.map(PlanningTarget<CoverageTracker.UncoveredBranch>::value),
            )
        }
    }
}

internal data class PlanningTarget<T>(
    val value: T,
    val methodId: String,
    val targetId: String,
    val statementIndex: Int,
    val successorOrdinal: Int = 0,
)

/** Pure layout kernel used by the EtsIR adapter and deterministic synthetic tests. */
internal fun <T> planTargetLayout(
    targets: Collection<PlanningTarget<T>>,
    topology: MethodTopology,
    shardSize: TargetShardSize,
): List<List<PlanningTarget<T>>> {
    if (targets.isEmpty()) return emptyList()
    require(targets.map(PlanningTarget<T>::methodId).distinct().size == 1) {
        "one target layout cannot mix methods"
    }

    val ordered = targets.sortedWith(planningTargetComparator())
    val duplicateIds = ordered.groupBy(PlanningTarget<T>::targetId).filterValues { it.size > 1 }.keys
    require(duplicateIds.isEmpty()) {
        "duplicate target IDs in shard planner: ${duplicateIds.sorted().joinToString()}"
    }
    val remaining = ordered.toMutableList()
    val limit = shardSize.limit ?: ordered.size
    val result = mutableListOf<List<PlanningTarget<T>>>()

    while (remaining.isNotEmpty()) {
        val shard = mutableListOf(remaining.removeAt(0))
        while (shard.size < limit && remaining.isNotEmpty()) {
            val next = remaining.minWithOrNull(
                compareBy<PlanningTarget<T>>(
                    { candidate ->
                        shard.minOf { selected ->
                            topology.dominatorDistance(selected.statementIndex, candidate.statementIndex)
                        }
                    },
                    PlanningTarget<T>::statementIndex,
                    PlanningTarget<T>::successorOrdinal,
                    PlanningTarget<T>::targetId,
                ),
            ) ?: error("non-empty target worklist has no minimum")
            remaining.remove(next)
            shard += next
        }
        result += shard
    }

    check(result.none(List<PlanningTarget<T>>::isEmpty))
    check(result.flatten().map(PlanningTarget<T>::targetId).distinct().size == ordered.size)
    return result
}

internal fun stableTargetShardId(
    methodId: String,
    zeroBasedIndex: Int,
    shardSize: TargetShardSize,
): TargetShardId {
    require(methodId.isNotBlank()) { "method ID must not be blank" }
    require(zeroBasedIndex >= 0) { "shard index must be non-negative" }
    return TargetShardId("$methodId#shard-${zeroBasedIndex + 1}-${shardSize.flagValue}")
}

private fun <T> planningTargetComparator(): Comparator<PlanningTarget<T>> =
    compareBy(
        PlanningTarget<T>::statementIndex,
        PlanningTarget<T>::successorOrdinal,
        PlanningTarget<T>::targetId,
    )

/** Method-local CFG topology and exact dominator-tree proximity. */
internal class MethodTopology private constructor(
    private val statementCount: Int,
    private val dominators: List<Set<Int>>,
    private val statementIndices: IdentityHashMap<EtsStmt, Int>?,
) {
    fun indexOf(statement: EtsStmt): Int = requireNotNull(statementIndices?.get(statement)) {
        "target statement is outside its method CFG"
    }

    fun dominatorDistance(first: Int, second: Int): Int {
        require(first in 0 until statementCount) { "statement index $first is outside CFG" }
        require(second in 0 until statementCount) { "statement index $second is outside CFG" }
        if (first == second) return 0

        val common = dominators[first].intersect(dominators[second])
        if (common.isEmpty()) return DISCONNECTED_DISTANCE + kotlin.math.abs(first - second)
        val commonDepth = common.maxOf { dominators[it].size - 1 }
        val firstDepth = dominators[first].size - 1
        val secondDepth = dominators[second].size - 1
        return firstDepth + secondDepth - 2 * commonDepth
    }

    companion object {
        private const val DISCONNECTED_DISTANCE: Int = 1_000_000

        fun from(method: EtsMethod): MethodTopology {
            val statements = method.cfg.stmts
            require(statements.isNotEmpty()) { "cannot plan targets for an empty CFG" }
            val indices = IdentityHashMap<EtsStmt, Int>()
            statements.forEachIndexed { index, statement -> indices[statement] = index }
            val predecessors = List(statements.size) { mutableSetOf<Int>() }
            statements.forEachIndexed { from, statement ->
                method.cfg.successors(statement).forEach { successor ->
                    indices[successor]?.let { to -> predecessors[to] += from }
                }
            }
            return fromPredecessors(predecessors, indices)
        }

        fun fromPredecessors(predecessors: List<Set<Int>>): MethodTopology =
            fromPredecessors(predecessors, statementIndices = null)

        private fun fromPredecessors(
            predecessors: List<Set<Int>>,
            statementIndices: IdentityHashMap<EtsStmt, Int>?,
        ): MethodTopology {
            require(predecessors.isNotEmpty()) { "CFG topology must contain an entry" }
            predecessors.forEachIndexed { node, incoming ->
                require(incoming.all { it in predecessors.indices }) {
                    "predecessor outside CFG at node $node"
                }
            }

            val allNodes = predecessors.indices.toSet()
            val dominators = MutableList(predecessors.size) { allNodes }
            dominators[0] = setOf(0)
            var changed: Boolean
            do {
                changed = false
                for (node in 1 until predecessors.size) {
                    val incoming = predecessors[node]
                    val next = if (incoming.isEmpty()) {
                        setOf(node)
                    } else {
                        incoming
                            .map(dominators::get)
                            .reduce(Set<Int>::intersect) + node
                    }
                    if (next != dominators[node]) {
                        dominators[node] = next
                        changed = true
                    }
                }
            } while (changed)

            return MethodTopology(predecessors.size, dominators, statementIndices)
        }
    }
}
