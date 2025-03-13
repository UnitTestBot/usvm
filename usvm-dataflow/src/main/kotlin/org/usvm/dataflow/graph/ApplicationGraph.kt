package org.usvm.dataflow.graph

/**
 * Provides both CFG and call graph (i.e., the supergraph in terms of RHS95 paper).
 */
interface ApplicationGraph<Method, Statement> {
    fun predecessors(node: Statement): Sequence<Statement>
    fun successors(node: Statement): Sequence<Statement>

    fun callees(node: Statement): Sequence<Method>
    fun callers(method: Method): Sequence<Statement>

    fun entryPoints(method: Method): Sequence<Statement>
    fun exitPoints(method: Method): Sequence<Statement>

    fun methodOf(node: Statement): Method
}
