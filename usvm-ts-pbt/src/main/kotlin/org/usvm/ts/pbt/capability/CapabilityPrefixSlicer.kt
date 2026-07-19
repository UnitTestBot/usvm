package org.usvm.ts.pbt.capability

import java.util.ArrayDeque

/** Deterministic CFG prefix computation. Invalid or partial CFGs stay probeable. */
object CapabilityPrefixSlicer {
    fun slice(
        methodId: String,
        branchId: String,
        targetStmtIndex: Int,
        cfg: CapabilityCfg,
    ): CapabilityPrefixSlice {
        val grouped = cfg.nodes.groupBy(CapabilityCfgNode::stmtIndex)
        val duplicateIndices = grouped.filterValues { it.size != 1 }.keys
        val nodes = grouped.mapValues { it.value.first() }
        val danglingEdges = nodes.values
            .flatMap(CapabilityCfgNode::successorStmtIndices)
            .filter { it !in nodes }
            .toSet()
        val structuralReason = when {
            duplicateIndices.isNotEmpty() -> "duplicate_cfg_stmt_index"
            cfg.entryStmtIndex !in nodes -> "missing_cfg_entry"
            targetStmtIndex !in nodes -> "target_stmt_missing_from_cfg"
            danglingEdges.isNotEmpty() -> "dangling_cfg_successor"
            else -> null
        }

        if (structuralReason != null) {
            return incompleteSlice(methodId, branchId, targetStmtIndex, nodes, structuralReason)
        }

        val forward = reachableFrom(cfg.entryStmtIndex) { nodes.getValue(it).successorStmtIndices }
        if (targetStmtIndex !in forward) {
            return incompleteSlice(methodId, branchId, targetStmtIndex, nodes, "target_not_reachable_from_entry")
        }

        val predecessors = nodes.keys.associateWith { mutableListOf<Int>() }
        nodes.values.forEach { node ->
            node.successorStmtIndices.forEach { successor -> predecessors.getValue(successor) += node.stmtIndex }
        }
        val backward = reachableFrom(targetStmtIndex) { predecessors.getValue(it) }
        val conservative = forward.intersect(backward).sorted()
        val mandatory = dominators(
            entry = cfg.entryStmtIndex,
            target = targetStmtIndex,
            reachable = forward,
            predecessors = predecessors,
        )
        val facts = conservative.flatMap { nodes.getValue(it).facts }.canonicalFacts()
        val mandatoryFacts = mandatory.flatMap { nodes.getValue(it).facts }.canonicalFacts()
        return CapabilityPrefixSlice(
            methodId = methodId,
            branchId = branchId,
            targetStmtIndex = targetStmtIndex,
            conservativeStmtIndices = conservative,
            mandatoryStmtIndices = mandatory,
            mandatoryFacts = mandatoryFacts,
            facts = facts,
            complete = true,
        )
    }

    private fun incompleteSlice(
        methodId: String,
        branchId: String,
        targetStmtIndex: Int,
        nodes: Map<Int, CapabilityCfgNode>,
        reason: String,
    ): CapabilityPrefixSlice {
        val known = nodes[targetStmtIndex]?.let { listOf(targetStmtIndex) }.orEmpty()
        return CapabilityPrefixSlice(
            methodId = methodId,
            branchId = branchId,
            targetStmtIndex = targetStmtIndex,
            conservativeStmtIndices = known,
            mandatoryStmtIndices = known,
            mandatoryFacts = known.flatMap { nodes.getValue(it).facts }.canonicalFacts(),
            facts = known.flatMap { nodes.getValue(it).facts }.canonicalFacts(),
            complete = false,
            uncertaintyReason = reason,
        )
    }

    private fun reachableFrom(start: Int, successors: (Int) -> Iterable<Int>): Set<Int> {
        val seen = linkedSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue
            successors(current).sorted().forEach(queue::addLast)
        }
        return seen
    }

    private fun dominators(
        entry: Int,
        target: Int,
        reachable: Set<Int>,
        predecessors: Map<Int, List<Int>>,
    ): List<Int> {
        val all = reachable.toSet()
        val dominators = reachable.associateWithTo(linkedMapOf()) { node ->
            if (node == entry) mutableSetOf(entry) else all.toMutableSet()
        }
        var changed: Boolean
        do {
            changed = false
            for (node in reachable.sorted()) {
                if (node == entry) continue
                val reachablePredecessors = predecessors.getValue(node).filter { it in reachable }
                val intersection = if (reachablePredecessors.isEmpty()) {
                    mutableSetOf()
                } else {
                    dominators.getValue(reachablePredecessors.first()).toMutableSet().also { common ->
                        reachablePredecessors.drop(1).forEach { predecessor ->
                            common.retainAll(dominators.getValue(predecessor))
                        }
                    }
                }
                intersection += node
                if (intersection != dominators.getValue(node)) {
                    dominators[node] = intersection
                    changed = true
                }
            }
        } while (changed)
        return dominators.getValue(target).sorted()
    }
}

internal fun List<CapabilityAstFact>.canonicalFacts(): List<CapabilityAstFact> =
    distinctBy { Triple(it.kind, it.evidence, it.proven) }
        .sortedWith(compareBy(CapabilityAstFact::kind, CapabilityAstFact::evidence, CapabilityAstFact::proven))
