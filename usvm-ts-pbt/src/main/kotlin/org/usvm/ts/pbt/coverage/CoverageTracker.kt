package org.usvm.ts.pbt.coverage

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.ts.pbt.interpreter.ExecutionListener
import org.usvm.ts.pbt.interpreter.VValue
import java.util.IdentityHashMap

/**
 * Statement + branch coverage shared by both phases of the hybrid analysis.
 *
 * A branch is an edge `(ifStmt, takenSuccessor)`. Identity semantics are used
 * for [EtsStmt] keys (statements are unique objects within a loaded scene).
 */
class CoverageTracker(
    /** The coverage zone: entry method + (optionally) other tracked methods. */
    methods: List<EtsMethod>,
) : ExecutionListener {

    data class BranchEdge(val ifStmt: EtsIfStmt, val successor: EtsStmt)

    data class UncoveredBranch(val method: EtsMethod, val edge: BranchEdge)

    data class Sample(
        val elapsedMs: Long,
        val phase: String,
        val coveredStmts: Int,
        val coveredBranches: Int,
    )

    private val zone: List<EtsMethod> = methods.filter { it.cfg.stmts.isNotEmpty() }

    val allStmts: Set<EtsStmt> = run {
        val set = newIdentitySet<EtsStmt>()
        zone.forEach { set.addAll(it.cfg.stmts) }
        set
    }

    val allBranches: Set<BranchEdge> = buildSet {
        for (method in zone) {
            for (stmt in method.cfg.stmts) {
                if (stmt is EtsIfStmt) {
                    method.cfg.successors(stmt).forEach { succ -> add(BranchEdge(stmt, succ)) }
                }
            }
        }
    }

    private val coveredStmts = newIdentitySet<EtsStmt>()
    private val coveredBranches = mutableSetOf<BranchEdge>()
    private val timelineSamples = mutableListOf<Sample>()

    private val startNanos = System.nanoTime()

    /** Label attributed to coverage recorded via listener callbacks. */
    var phase: String = "pbt"

    val coveredStmtCount: Int get() = coveredStmts.size
    val coveredBranchCount: Int get() = coveredBranches.size
    val timeline: List<Sample> get() = timelineSamples

    fun stmtCoverage(): Double =
        if (allStmts.isEmpty()) 1.0 else coveredStmts.size.toDouble() / allStmts.size

    fun branchCoverage(): Double =
        if (allBranches.isEmpty()) 1.0 else coveredBranches.size.toDouble() / allBranches.size

    fun isCovered(edge: BranchEdge): Boolean = edge in coveredBranches

    /** The worklist for the symbolic phase: branch edges never taken. */
    fun uncoveredBranches(): List<UncoveredBranch> {
        val methodOf = IdentityHashMap<EtsStmt, EtsMethod>()
        zone.forEach { m -> m.cfg.stmts.forEach { methodOf[it] = m } }
        return allBranches
            .filter { it !in coveredBranches }
            .mapNotNull { edge -> methodOf[edge.ifStmt]?.let { UncoveredBranch(it, edge) } }
    }

    fun uncoveredStmts(): List<EtsStmt> = allStmts.filter { it !in coveredStmts }

    // -- ExecutionListener --------------------------------------------------

    override fun onStmt(stmt: EtsStmt) {
        if (stmt in allStmts && coveredStmts.add(stmt)) {
            recordSample()
        }
    }

    override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {
        if (ifStmt in allStmts && coveredBranches.add(BranchEdge(ifStmt, taken))) {
            recordSample()
        }
    }

    override fun onMethodEnter(method: EtsMethod, thisValue: VValue, args: List<VValue>) {}

    /** Merge a symbolic-phase trace (e.g. `state.pathNode.allStatements`) as covered. */
    fun mergeTrace(stmts: List<EtsStmt>) {
        var newCoverage = false
        for (stmt in stmts) {
            if (stmt in allStmts && coveredStmts.add(stmt)) newCoverage = true
        }
        // Recover branch edges from consecutive pairs
        for ((a, b) in stmts.zipWithNext()) {
            if (a is EtsIfStmt && a in allStmts && coveredBranches.add(BranchEdge(a, b))) {
                newCoverage = true
            }
        }
        if (newCoverage) recordSample()
    }

    private fun recordSample() {
        timelineSamples += Sample(
            elapsedMs = (System.nanoTime() - startNanos) / 1_000_000,
            phase = phase,
            coveredStmts = coveredStmts.size,
            coveredBranches = coveredBranches.size,
        )
    }

    private companion object {
        fun <T> newIdentitySet(): MutableSet<T> =
            java.util.Collections.newSetFromMap(IdentityHashMap())
    }
}
