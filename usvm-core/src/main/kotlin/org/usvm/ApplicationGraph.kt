package org.usvm

interface ApplicationGraph<Method, Statement> {
    fun predecessors(node: Statement): Sequence<Statement>
    fun successors(node: Statement): Sequence<Statement>

    fun callees(node: Statement): Sequence<Method>
    fun callers(method: Method): Sequence<Statement>

    fun entryPoint(method: Method): Sequence<Statement>
    fun exitPoints(method: Method): Sequence<Statement>

    fun methodOf(node: Statement): Method
}
