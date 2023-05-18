package org.usvm.interpreter

import org.usvm.ApplicationGraph
import org.usvm.language.Call
import org.usvm.language.Goto
import org.usvm.language.If
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.Return
import org.usvm.language.SetLabel
import org.usvm.language.SetValue
import org.usvm.language.Stmt

class SampleApplicationGraph(
    program: Program,
) : ApplicationGraph<Method<*>, Stmt> {
    private val stmtToMethod = program.methods
        .flatMap { method -> method.body?.stmts?.map { it to method }.orEmpty() }
        .toMap()

    private val stmtToCallees = stmtToMethod.keys
        .associateWith { findCallees(it) }
        .withDefault { emptyList() }

    private val methodToCallers = stmtToCallees
        .flatMap { (stmt, methods) -> methods.map { it to stmt } }
        .groupBy({ it.first }) { it.second }
        .withDefault { emptyList() }

    private fun findCallees(stmt: Stmt): List<Method<*>> =
        if (stmt is Call) {
            listOf(stmt.method)
        } else {
            emptyList()
        }

    private val stmtToInfo = mutableMapOf<Stmt, StmtInfo>()
    private val methodToInfo = mutableMapOf<Method<*>, MethodInfo>()

    private fun lazyGetStmtInfo(stmt: Stmt): StmtInfo {
        if (stmt !in stmtToInfo) {
            val method = stmtToMethod.getValue(stmt)
            computeAllForMethod(method)
        }
        return stmtToInfo.getValue(stmt)
    }

    private fun lazyGetMethodInfo(method: Method<*>): MethodInfo {
        if (method !in methodToInfo) {
            computeAllForMethod(method)
        }
        return methodToInfo.getValue(method)
    }

    private fun computeAllForMethod(method: Method<*>) {
        if (method.body == null) {
            methodToInfo[method] = MethodInfo(null, emptyList(), emptyList())
            return
        }

        val exitPoints = method.body.stmts.filterIsInstance<Return>()
        methodToInfo[method] = MethodInfo(method.body.stmts.first(), exitPoints, methodToCallers.getValue(method))


        // now collect stmt infos for each stmt in the method.body


        val labelToStmt = method.body.stmts.filterIsInstance<SetLabel>().associateBy { it.label }

        val stmtToSuccsAsPairs = mutableListOf<Pair<Stmt, Stmt>>()

        for ((idx, stmt) in method.body.stmts.withIndex()) {
            val nxtStmt = if (idx + 1 < method.body.stmts.size) {
                method.body.stmts[idx + 1]
            } else {
                null
            }

            val succs = when (stmt) {
                is Goto -> listOf(labelToStmt.getValue(stmt.label))
                is If -> listOfNotNull(labelToStmt.getValue(stmt.label), nxtStmt)
                is Return -> emptyList()
                is Call,
                is SetLabel,
                is SetValue,
                -> listOfNotNull(nxtStmt)
            }

            stmtToSuccsAsPairs += succs.map { stmt to it }
        }

        val stmtToSuccs = stmtToSuccsAsPairs.groupBy({ it.first }) { it.second }
        val stmtToPreds = stmtToSuccsAsPairs.groupBy({ it.second }) { it.first }

        for (stmt in method.body.stmts) {
            val info =
                StmtInfo(method, stmtToPreds[stmt].orEmpty(), stmtToSuccs[stmt].orEmpty(), stmtToCallees[stmt].orEmpty())
            stmtToInfo[stmt] = info
        }
    }

    override fun predecessors(node: Stmt): Sequence<Stmt> {
        val info = lazyGetStmtInfo(node)
        return info.predecessors.asSequence()
    }

    override fun successors(node: Stmt): Sequence<Stmt> {
        val info = lazyGetStmtInfo(node)
        return info.successors.asSequence()
    }

    override fun callees(node: Stmt): Sequence<Method<*>> {
        val info = lazyGetStmtInfo(node)
        return info.callees.asSequence()
    }

    override fun callers(method: Method<*>): Sequence<Stmt> {
        val info = lazyGetMethodInfo(method)
        return info.callers.asSequence()
    }

    override fun entryPoint(method: Method<*>): Sequence<Stmt> {
        val info = lazyGetMethodInfo(method)
        return listOfNotNull(info.entryPoint).asSequence()
    }

    override fun exitPoints(method: Method<*>): Sequence<Stmt> {
        val info = lazyGetMethodInfo(method)
        return info.exitPoints.asSequence()
    }

    override fun methodOf(node: Stmt): Method<*> {
        val info = lazyGetStmtInfo(node)
        return info.method
    }

    private data class StmtInfo(
        val method: Method<*>,
        val predecessors: List<Stmt>,
        val successors: List<Stmt>,
        val callees: List<Method<*>>,
    )

    private data class MethodInfo(
        val entryPoint: Stmt?,
        val exitPoints: List<Stmt>,
        val callers: List<Stmt>,
    )
}