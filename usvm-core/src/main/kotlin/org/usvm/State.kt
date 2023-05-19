package org.usvm

import kotlinx.collections.immutable.PersistentList
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

abstract class UState<Type, Field, Method, Statement>(
    val pathConstraints: UPathConstraints<Type>,
    val memory: UMemoryBase<Field, Type, Method>,
    val callStack: UCallStack<Method, Statement>,
    var models: List<UModel>,
    var path: PersistentList<Statement>,
) {
    /**
     * Creates new state structurally identical to this.
     * If [newConstraints] is null, clones [pathConstraints]. Otherwise, uses [newConstraints] in cloned state.
     */
    abstract fun clone(newConstraints: UPathConstraints<Type>? = null): UState<Type, Field, Method, Statement>

    val lastEnteredMethod: Method
        get() = callStack.lastMethod()
}

val <Statement> UState<*, *, *, Statement>.lastStmtOrNull: Statement? get() = path.lastOrNull()

val <Statement> UState<*, *, *, Statement>.lastStmt: Statement get() = path.last()