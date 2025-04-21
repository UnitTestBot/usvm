package org.usvm.api.checkers

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver

data class UncoveredIfSuccessors(val ifStmt: EtsIfStmt, val successors: Set<EtsStmt>)

class UnreachableCodeDetector : TsInterpreterObserver, UMachineObserver<TsState> {
    private val uncoveredSuccessorsOfVisitedIfStmts = hashMapOf<EtsMethod, MutableMap<EtsIfStmt, MutableSet<EtsStmt>>>()
    lateinit var result: Map<EtsMethod, List<UncoveredIfSuccessors>>

    override fun onIfStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsIfStmt, scope: TsStepScope) {
        val ifStmts = uncoveredSuccessorsOfVisitedIfStmts.getOrPut(stmt.method) { mutableMapOf() }
        // We've already added its successors in the map
        if (stmt in ifStmts) return

        val successors = stmt.method.cfg.successors(stmt)
        ifStmts[stmt] = successors.toMutableSet()
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        val previousStatement = parent.pathNode.parent?.statement
        if (previousStatement !is EtsIfStmt) return

        val method = parent.currentStatement.method
        val remainingUncoveredIfSuccessors = uncoveredSuccessorsOfVisitedIfStmts.getValue(method)
        val remainingSuccessorsForCurrentIf = remainingUncoveredIfSuccessors[previousStatement]
            ?: return // No uncovered successors for this if statement

        remainingSuccessorsForCurrentIf -= parent.currentStatement
        forks.forEach { remainingSuccessorsForCurrentIf -= it.currentStatement }
    }

    override fun onMachineStopped() {
        result = uncoveredSuccessorsOfVisitedIfStmts
            .mapNotNull { (method, uncoveredIfSuccessors) ->
                uncoveredIfSuccessors
                    .filter { it.value.isNotEmpty() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { ifSucc -> method to ifSucc.map { UncoveredIfSuccessors(it.key, it.value) } }
            }.toMap()
    }
}
