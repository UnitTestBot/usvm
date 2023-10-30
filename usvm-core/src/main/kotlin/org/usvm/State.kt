package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.UUnknownResult
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
    open var pathLocation: PathsTrieNode<State, Statement>,
    targets: List<Target> = emptyList(),
) where Context : UContext<*>,
        Target : UTarget<Statement, Target>,
        State : UState<Type, Method, Statement, Context, Target, State> {
    /**
     * Deterministic state id.
     * TODO: Can be replaced with overridden hashCode
     */
    val id: StateId = ctx.getNextStateId()

    val reversedPath: Iterator<Statement>
        get() = object : Iterator<Statement> {
            var currentLocation: PathsTrieNode<State, Statement>? = pathLocation

            override fun hasNext(): Boolean = currentLocation !is RootNode

            override fun next(): Statement {
                if (!hasNext()) throw NoSuchElementException()

                val current = requireNotNull(currentLocation)
                val nextStmt = current.statement

                currentLocation = current.parent

                return nextStmt
            }

        }

    /**
     * Creates new state structurally identical to this.
     * If [newConstraints] is null, clones [pathConstraints]. Otherwise, uses [newConstraints] in cloned state.
     */
    abstract fun clone(newConstraints: UPathConstraints<Type>? = null): State

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UState<*, *, *, *, *, *>

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    val lastEnteredMethod: Method
        get() = callStack.lastMethod()

    val currentStatement: Statement
        get() = pathLocation.statement

    /**
     * A property containing information about whether the state is exceptional or not.
     */
    abstract val isExceptional: Boolean

    protected var targetsImpl: PersistentList<Target> = targets.toPersistentList()
        private set

    private val reachedTerminalTargetsImpl = mutableSetOf<Target>()

    /**
     * Collection of state's current targets.
     * TODO: clean removed targets sometimes
     */
    val targets: Sequence<Target> get() = targetsImpl.asSequence().filterNot { it.isRemoved }

    /**
     * Reached targets with no children.
     */
    val reachedTerminalTargets: Set<Target> = reachedTerminalTargetsImpl

    /**
     * If the [target] is not removed and is contained in this state's target collection,
     * removes it from there and adds there all its children.
     *
     * @return true if the [target] was successfully removed.
     */
    internal fun tryPropagateTarget(target: Target): Boolean {
        val previousTargetCount = targetsImpl.size
        targetsImpl = targetsImpl.remove(target)

        if (previousTargetCount == targetsImpl.size || target.isRemoved) {
            return false
        }

        if (target.isTerminal) {
            reachedTerminalTargetsImpl.add(target)
            return true
        }

        targetsImpl = targetsImpl.addAll(target.children)

        return true
    }
}
