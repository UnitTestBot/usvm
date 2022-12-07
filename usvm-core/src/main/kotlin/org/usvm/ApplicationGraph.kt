package org.usvm

interface ApplicationGraph<Method, Statement> {
    fun predecessors(node: Statement): Iterable<Statement>
    fun successors(node: Statement): Iterable<Statement>

    fun callees(node: Statement): Iterable<Method>
    fun callers(method: Method): Iterable<Statement>

    fun entryPoint(method: Method): Statement?
    fun exitPoints(method: Method): Iterable<Statement>

    fun methodOf(node: Statement): Method
}
