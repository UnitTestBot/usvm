package org.usvm

import io.ksmt.expr.KInterpretedValue
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

typealias StateId = UInt

abstract class UState<Type, Method, Statement, Context : UContext, Target : UTarget<Method, Statement, Target, State>, State : UState<Type, Method, Statement, Context, Target, State>>(
    // TODO: add interpreter-specific information
    ctx: UContext,
    open val callStack: UCallStack<Method, Statement>,
    open val pathConstraints: UPathConstraints<Type, Context>,
    open val memory: UMemory<Type, Method>,
    open var models: List<UModelBase<Type>>,
    open var pathLocation: PathsTrieNode<State, Statement>,
    targets: Collection<Target> = emptyList()
) {
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
    abstract fun clone(newConstraints: UPathConstraints<Type, Context>? = null): State

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

    private val currentTargets = targets.toMutableList()
    private val reachedSinksImpl = mutableSetOf<Target>()

    /**
     * Collection of state's current targets.
     * TODO: clean removed targets sometimes
     */
    val targets: Collection<Target> get() = currentTargets.filterNot { it.isRemoved }

    /**
     * Reached targets with no children.
     */
    val reachedSinks: Set<Target> = reachedSinksImpl

    /**
     * Tries to remove the [target] from current targets collection and
     * add its children there.
     *
     * @return true if the [target] was successfully removed.
     */
    internal fun visitTarget(target: Target): Boolean {
        if (currentTargets.remove(target) && !target.isRemoved) {
            if (target.isSink) {
                reachedSinksImpl.add(target)
            } else {
                currentTargets.addAll(target.children)
            }
            return true
        }
        return false
    }
}

data class ForkResult<T>(
    val positiveState: T?,
    val negativeState: T?,
)

private typealias StateToCheck = Boolean

private const val ForkedState = true
private const val OriginalState = false

/**
 * Checks [newConstraintToOriginalState] or [newConstraintToForkedState], depending on the value of [stateToCheck].
 * Depending on the result of checking this condition, do the following:
 * - On [UUnsatResult] - returns `null`;
 * - On [UUnknownResult] - adds [newConstraintToOriginalState] to the path constraints of the [state],
 * iff [addConstraintOnUnknown] is `true`, and returns null;
 * - On [USatResult] - clones the original state and adds the [newConstraintToForkedState] to it, adds [newConstraintToOriginalState]
 * to the original state, sets the satisfiable model to the corresponding state depending on the [stateToCheck], and returns the
 * forked state.
 *
 */
private fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext> forkIfSat(
    state: T,
    newConstraintToOriginalState: UBoolExpr,
    newConstraintToForkedState: UBoolExpr,
    stateToCheck: StateToCheck,
    addConstraintOnUnknown: Boolean = true,
): T? {
    val constraintsToCheck = state.pathConstraints.clone()

    constraintsToCheck += if (stateToCheck) {
        newConstraintToForkedState
    } else {
        newConstraintToOriginalState
    }
    val solver = newConstraintToForkedState.uctx.solver<Type, Context>()
    val satResult = solver.checkWithSoftConstraints(constraintsToCheck)

    return when (satResult) {
        is UUnsatResult -> null

        is USatResult -> {
            // Note that we cannot extract common code here due to
            // heavy plusAssign operator in path constraints.
            // Therefore, it is better to reuse already constructed [constraintToCheck].
            if (stateToCheck) {
                val forkedState = state.clone(constraintsToCheck)
                state.pathConstraints += newConstraintToOriginalState
                forkedState.models = listOf(satResult.model)
                forkedState
            } else {
                val forkedState = state.clone()
                state.pathConstraints += newConstraintToOriginalState
                state.models = listOf(satResult.model)
                // TODO: implement path condition setter (don't forget to reset UMemoryBase:types!)
                forkedState.pathConstraints += newConstraintToForkedState
                forkedState
            }
        }

        is UUnknownResult -> {
            if (addConstraintOnUnknown) {
                state.pathConstraints += newConstraintToOriginalState
            }
            null
        }
    }
}


/**
 * Implements symbolic branching.
 * Checks if [condition] and ![condition] are satisfiable within [state].
 * If both are satisfiable, copies [state] and extends path constraints.
 * If only one is satisfiable, returns only [state] (possibly with [condition] added to path constraints).
 *
 * Important contracts:
 * 1. in return value, at least one of [ForkResult.positiveState] and [ForkResult.negativeState] is not null;
 * 2. makes not more than one query to USolver;
 * 3. if both [condition] and ![condition] are satisfiable, then [ForkResult.positiveState] === [state].
 */
fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext> fork(
    state: T,
    condition: UBoolExpr,
): ForkResult<T> {
    val (trueModels, falseModels) = state.models.partition { model ->
        val holdsInModel = model.eval(condition)
        check(holdsInModel is KInterpretedValue<UBoolSort>) {
            "Evaluation in model: expected true or false, but got $holdsInModel"
        }
        holdsInModel.isTrue
    }

    val notCondition = condition.uctx.mkNot(condition)
    val (posState, negState) = when {

        trueModels.isNotEmpty() && falseModels.isNotEmpty() -> {
            val posState = state
            val negState = state.clone()

            posState.models = trueModels
            negState.models = falseModels
            posState.pathConstraints += condition
            negState.pathConstraints += notCondition

            posState to negState
        }

        trueModels.isNotEmpty() -> state to forkIfSat(
            state,
            newConstraintToOriginalState = condition,
            newConstraintToForkedState = notCondition,
            stateToCheck = ForkedState
        )

        falseModels.isNotEmpty() -> {
            val forkedState = forkIfSat(
                state,
                newConstraintToOriginalState = condition,
                newConstraintToForkedState = notCondition,
                stateToCheck = OriginalState
            )

            if (forkedState != null) {
                state to forkedState
            } else {
                null to state
            }
        }

        else -> error("[trueModels] and [falseModels] are both empty, that has to be impossible by construction!")
    }

    return ForkResult(posState, negState)
}

/**
 * Implements symbolic branching on few disjoint conditions.
 *
 * @return a list of states for each condition - `null` state
 * means [UUnknownResult] or [UUnsatResult] of checking condition.
 */
fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext> forkMulti(
    state: T,
    conditions: Iterable<UBoolExpr>,
): List<T?> {
    var curState = state
    val result = mutableListOf<T?>()
    for (condition in conditions) {
        val (trueModels, _) = curState.models.partition { model ->
            val holdsInModel = model.eval(condition)
            check(holdsInModel is KInterpretedValue<UBoolSort>) {
                "Evaluation in $model on condition $condition: expected true or false, but got $holdsInModel"
            }
            holdsInModel.isTrue
        }

        val nextRoot = if (trueModels.isNotEmpty()) {
            val root = curState.clone()
            curState.models = trueModels
            curState.pathConstraints += condition

            root
        } else {
            val root = forkIfSat(
                curState,
                newConstraintToOriginalState = condition,
                newConstraintToForkedState = condition.ctx.trueExpr,
                stateToCheck = OriginalState,
                addConstraintOnUnknown = false
            )

            root
        }

        if (nextRoot != null) {
            result += curState
            curState = nextRoot
        } else {
            result += null
        }
    }

    return result
}
