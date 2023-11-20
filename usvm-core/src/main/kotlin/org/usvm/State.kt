package org.usvm

import io.ksmt.expr.KInterpretedValue
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.merging.UMergeable
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.targets.UTargetsSet
import org.usvm.targets.UTarget

typealias StateId = UInt

abstract class UState<Type, Method, Statement, Context, Target, State>(
    // TODO: add interpreter-specific information
    val ctx: Context,
    open val callStack: UCallStack<Method, Statement>,
    open val pathConstraints: UPathConstraints<Type>,
    open val memory: UMemory<Type, Method>,
    /**
     * A list of [UModelBase]s that satisfy the [pathConstraints].
     * Could be empty (for example, if forking without a solver).
     */
    open var models: List<UModelBase<Type>>,
    open var pathNode: PathNode<Statement>,
    open val targets: UTargetsSet<Target, Statement>,
) : UMergeable<State, Unit>
    where Context : UContext<*>,
          Target : UTarget<Statement, Target>,
          State : UState<Type, Method, Statement, Context, Target, State> {
    /**
     * Deterministic state id.
     * TODO: Can be replaced with overridden hashCode
     */
    val id: StateId = ctx.getNextStateId()

    /**
     * Creates new state structurally identical to this.
     * If [newConstraints] is null, clones [pathConstraints]. Otherwise, uses [newConstraints] in cloned state.
     */
    abstract fun clone(newConstraints: UPathConstraints<Type>? = null): State

    override fun mergeWith(other: State, by: Unit): State? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UState<*, *, *, *, *, *>

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    abstract val entrypoint: Method

    val lastEnteredMethod: Method
        get() = callStack.lastMethod()

    val currentStatement: Statement
        get() = pathNode.statement

    /**
     * A property containing information about whether the state is exceptional or not.
     */
    abstract val isExceptional: Boolean
}
