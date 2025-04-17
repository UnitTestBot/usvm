package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsUnaryExpr
import org.jacodb.ets.model.EtsValue
import java.util.BitSet

interface LiveVariables {
    fun isAliveAt(local: String, stmt: EtsStmt): Boolean

    companion object {
        private const val THRESHOLD: Int = 20

        fun from(method: EtsMethod): LiveVariables =
            if (method.cfg.stmts.size > THRESHOLD) LiveVariablesImpl(method) else AlwaysAlive
    }
}

object AlwaysAlive : LiveVariables {
    override fun isAliveAt(local: String, stmt: EtsStmt): Boolean = true
}

class LiveVariablesImpl(
    val method: EtsMethod,
) : LiveVariables {
    companion object {
        private fun EtsEntity.used(): List<String> = when (this) {
            is EtsValue -> this.used()
            is EtsUnaryExpr -> arg.used()
            is EtsBinaryExpr -> left.used() + right.used()
            is EtsCallExpr -> this.used()
            is EtsCastExpr -> arg.used()
            is EtsInstanceOfExpr -> arg.used()
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
    }

    private val aliveAtStmt: Array<BitSet>
    private val indexOfName = hashMapOf<String, Int>()
    private val definedAtStmt = IntArray(method.cfg.stmts.size) { -1 }

    private fun emptyBitSet() = BitSet(indexOfName.size)
    private fun BitSet.copy() = clone() as BitSet

    init {
        for (stmt in method.cfg.stmts) {
            if (stmt is EtsAssignStmt) {
                when (val lhv = stmt.lhv) {
                    is EtsLocal -> {
                        val lhvIndex = indexOfName.size
                        definedAtStmt[stmt.location.index] = lhvIndex
                        indexOfName[lhv.name] = lhvIndex
                    }
                }
            }
        }

        aliveAtStmt = Array(method.cfg.stmts.size) { emptyBitSet() }

        val queue = method.cfg.stmts.toHashSet()
        while (queue.isNotEmpty()) {
            val stmt = queue.first()
            queue.remove(stmt)

            val aliveHere = emptyBitSet().apply {
                val usedLocals = when (stmt) {
                    is EtsAssignStmt -> stmt.lhv.used() + stmt.rhv.used()
                    is EtsCallStmt -> stmt.expr.used()
                    is EtsReturnStmt -> stmt.returnValue?.used().orEmpty()
                    is EtsIfStmt -> stmt.condition.used()
                    is EtsThrowStmt -> stmt.exception.used()
                    else -> emptyList()
                }

                usedLocals.mapNotNull { indexOfName[it] }.forEach { set(it) }
            }

            for (succ in method.cfg.successors(stmt)) {
                val transferFromSucc = aliveAtStmt[succ.location.index].copy()
                val definedAtSucc = definedAtStmt[succ.location.index]
                if (definedAtSucc != -1) {
                    transferFromSucc.clear(definedAtSucc)
                }

                aliveHere.or(transferFromSucc)
            }

            if (aliveHere != aliveAtStmt[stmt.location.index]) {
                aliveAtStmt[stmt.location.index] = aliveHere
                if (stmt !in method.cfg.entries) {
                    queue.addAll(method.cfg.predecessors(stmt))
                }
            }
        }
    }

    override fun isAliveAt(local: String, stmt: EtsStmt): Boolean {
        if (stmt.location.index < 0) return true
        val index = indexOfName[local] ?: return true
        return aliveAtStmt[stmt.location.index].get(index)
    }
}
