package org.usvm.api.checkers

import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver

class UnreachableCodeDetector : TsInterpreterObserver, UMachineObserver<TsState> {
    private val uncoveredIfSuccessors = hashMapOf<EtsMethod, MutableSet<EtsStmt>>()
    private val allIfSuccessors = hashMapOf<EtsMethod, MutableSet<EtsStmt>>()
    private val visitedIfStmt = hashMapOf<EtsMethod, MutableSet<EtsIfStmt>>()

    lateinit var result: Map<EtsMethod, Set<Pair<EtsIfStmt, Set<EtsStmt>>>>

    override fun onStatePeeked(state: TsState) {
        val method = state.currentStatement.method
        if (method !in allIfSuccessors) {
            val ifSuccessors = method.cfg.stmts
                .filter { it is EtsIfStmt }
                .flatMapTo(hashSetOf()) { method.cfg.successors(it) }
            allIfSuccessors[method] = ifSuccessors
            uncoveredIfSuccessors[method] = ifSuccessors.toHashSet()
        }
    }

    override fun onIfStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsIfStmt, scope: TsStepScope) {
        visitedIfStmt.getOrPut(stmt.method) { hashSetOf() }.add(stmt)
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        if (parent.pathNode.parent?.statement !is EtsIfStmt) return

        val currentIfSuccessors = uncoveredIfSuccessors.getValue(parent.currentStatement.method)
        currentIfSuccessors -= parent.currentStatement
        forks.forEach { currentIfSuccessors -= it.currentStatement }
    }

    override fun onMachineStopped() {
        result = uncoveredIfSuccessors.map { (method, values) ->
            val visitedIfs = visitedIfStmt.getValue(method)
            val values = values.groupBy { method.cfg.predecessors(it).single() }
                .map { it.key as EtsIfStmt to it.value.toSet() }
                .filter { it.first in visitedIfs }
                .toSet()
            method to values
        }.toMap()
    }
}