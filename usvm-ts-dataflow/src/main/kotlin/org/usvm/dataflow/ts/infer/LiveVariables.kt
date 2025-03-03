package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod

interface LiveVariables {
    fun isAliveAt(local: String, stmt: EtsStmt): Boolean

    companion object {
        private const val THRESHOLD: Int = 20

        fun from(method: EtsMethod): LiveVariables =
            if (method.cfg.stmts.size > THRESHOLD)
                LiveVariablesImpl(method)
            else AlwaysAlive
    }
}

object AlwaysAlive : LiveVariables {
    override fun isAliveAt(local: String, stmt: EtsStmt): Boolean = true
}

class LiveVariablesImpl(
    val method: EtsMethod
) : LiveVariables {
    companion object {
        private fun EtsEntity.used(): List<String> = when (this) {
            is EtsValue -> this.used()
            is EtsUnaryExpr -> arg.used()
            is EtsBinaryExpr -> left.used() + right.used()
            is EtsCallExpr -> this.used()
            is EtsCastExpr -> arg.used()
            is EtsInstanceOfExpr -> arg.used()
            is EtsTernaryExpr -> condition.used() + thenExpr.used() + elseExpr.used()
            else -> emptyList()
        }

        private fun EtsValue.used(): List<String> = when (this) {
            is EtsLocal -> listOf(name)
            is EtsInstanceFieldRef -> listOf(instance.name)
            is EtsArrayAccess -> array.used() + index.used()
            else -> emptyList()
        }

        private fun EtsCallExpr.used(): List<String> = when (this) {
            is EtsInstanceCallExpr -> listOf(instance.name) + args.flatMap { it.used() }
            else -> args.flatMap { it.used() }
        }

        private fun <T> postOrder(sources: Iterable<T>, successors: (T) -> Iterable<T>): List<T> {
            val order = mutableListOf<T>()
            val visited = hashSetOf<T>()

            fun dfs(node: T) {
                visited.add(node)
                for (next in successors(node)) {
                    if (next !in visited)
                        dfs(next)
                }
                order.add(node)
            }

            for (source in sources) {
                if (source !in visited)
                    dfs(source)
            }

            return order
        }
    }

    private fun stronglyConnectedComponents(): IntArray {
        val rpo = postOrder(method.cfg.entries) {
            method.cfg.successors(it)
        }.reversed()

        val coloring = IntArray(method.cfg.stmts.size) { -1 }
        var nextColor = 0
        fun backwardDfs(stmt: EtsStmt) {
            coloring[stmt.location.index] = nextColor
            if (stmt in method.cfg.entries)
                return
            for (next in method.cfg.predecessors(stmt)) {
                if (coloring[next.location.index] == -1) {
                    backwardDfs(next)
                }
            }
        }

        for (stmt in rpo) {
            if (coloring[stmt.location.index] == -1) {
                backwardDfs(stmt)
                nextColor++
            }
        }

        return coloring
    }

    private fun condensation(coloring: IntArray): Map<Int, Set<Int>> {
        val successors = hashMapOf<Int, HashSet<Int>>()
        for (from in method.cfg.stmts) {
            for (to in method.cfg.successors(from)) {
                val fromColor = coloring[from.location.index]
                val toColor = coloring[to.location.index]
                if (fromColor != toColor) {
                    successors.computeIfAbsent(fromColor) { hashSetOf() }
                        .add(toColor)
                }
            }
        }
        return successors
    }

    private val lifetime = hashMapOf<String, Pair<Int, Int>>()
    private val coloring = stronglyConnectedComponents()
    private val colorIndex: IntArray

    init {
        val condensationSuccessors = condensation(coloring)
        val entriesColors = method.cfg.entries.map {
            coloring[it.location.index]
        }
        val colorOrder = postOrder(entriesColors) {
            condensationSuccessors[it].orEmpty()
        }.reversed()

        colorIndex = IntArray(colorOrder.size) { -1 }
        for ((index, color) in colorOrder.withIndex()) {
            colorIndex[color] = index
        }

        val ownLocals = hashSetOf<String>()
        for (stmt in method.cfg.stmts) {
            if (stmt is EtsAssignStmt) {
                when (val lhv = stmt.lhv) {
                    is EtsLocal -> ownLocals.add(lhv.name)
                }
            }
        }

        for (stmt in method.cfg.stmts) {
            val usedLocals = when (stmt) {
                is EtsAssignStmt -> stmt.lhv.used() + stmt.rhv.used()
                is EtsCallStmt -> stmt.expr.used()
                is EtsReturnStmt -> stmt.returnValue?.used().orEmpty()
                is EtsIfStmt -> stmt.condition.used()
                is EtsSwitchStmt -> stmt.arg.used()
                is EtsThrowStmt -> stmt.arg.used()
                else -> emptyList()
            }
            val blockIndex = colorIndex[coloring[stmt.location.index]]

            for (local in usedLocals) {
                if (local in ownLocals) {
                    lifetime.merge(local, blockIndex to blockIndex) { (begin, end), (bb, be) ->
                        minOf(begin, bb) to maxOf(end, be)
                    }
                } else {
                    lifetime[local] = Int.MIN_VALUE to Int.MAX_VALUE
                }
            }
        }
    }

    override fun isAliveAt(local: String, stmt: EtsStmt): Boolean {
        if (stmt.location.index < 0) return true
        val block = colorIndex[coloring[stmt.location.index]]
        val (begin, end) = lifetime[local] ?: error("Unknown local")
        return block in begin..end
    }
}
