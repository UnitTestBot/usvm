package org.usvm.interpreter.accessors

import org.usvm.ApplicationGraph
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.lastStmt

interface UBaseOperations<T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> {
    fun T.callInitialMethod(method: Method)

    fun T.callMethod(method: Method, arguments: List<UExpr<out USort>>)
    fun T.newStmt(stmt: Statement)
    fun successors(stmt: Statement): Sequence<Statement>
    fun T.popFrame()
}

class UBaseOperationsImpl<T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement>(
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val methodToArgumentsCount: (Method) -> Int,
    private val methodToLocalsCount: (Method) -> Int,
) : UBaseOperations<T, Type, Field, Method, Statement> {
    override fun T.callInitialMethod(method: Method) {
        val entryPoint = applicationGraph.entryPoint(method).single()
        callStack.push(method, returnSite = null)
        memory.stack.push(methodToArgumentsCount(method), methodToLocalsCount(method))
        newStmt(entryPoint)
    }

    override fun T.callMethod(method: Method, arguments: List<UExpr<out USort>>) {
        val entryPoint = applicationGraph.entryPoint(method).single()
        val returnSite = lastStmt
        callStack.push(method, returnSite)
        memory.stack.push(arguments.toTypedArray(), methodToLocalsCount(method))
        newStmt(entryPoint)
    }

    override fun T.newStmt(stmt: Statement) {
        path = path.add(stmt)
    }

    override fun successors(stmt: Statement): Sequence<Statement> {
        return applicationGraph.successors(stmt)
    }

    override fun T.popFrame() {
        val returnSite = callStack.pop()
        memory.stack.pop()

        if (returnSite != null) {
            newStmt(returnSite)
        }
    }
}

