package org.usvm

import io.ksmt.expr.KInterpretedValue
import kotlinx.collections.immutable.PersistentList
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

abstract class UState<Type, Field, Method, Statement>(
    // TODO: add interpreter-specific information
    val ctx: UContext,
    val callStack: UCallStack<Method, Statement>,
    val pathConstraints: UPathConstraints<Type>,
    val memory: UMemoryBase<Field, Type, Method>,
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

    // TODO or last? Do we add a current stmt into the path immediately?
    val currentStatement: Statement?
        get() = path.lastOrNull()
}

class ForkResult<T>(
    val positiveState: T?,
    val negativeState: T?,
) {
    operator fun component1(): T? = positiveState
    operator fun component2(): T? = negativeState
}

/**
 * Checks if [conditionToCheck] is satisfiable within path constraints of [state].
 * If it does, clones [state] and returns it with enriched constraints:
 * - if [forkToSatisfied], then adds constraint [satisfiedCondition];
 * - if ![forkToSatisfied], then adds constraint [conditionToCheck].
 * Otherwise, returns null.
 * If [conditionToCheck] is not unsatisfiable (i.e., solver returns sat or unknown),
 * mutates [state] by adding new path constraint c:
 * - if [forkToSatisfied], then c = [conditionToCheck]
 * - if ![forkToSatisfied], then c = [satisfiedCondition]
 */
private fun <T : UState<Type, *, *, *>, Type> forkIfSat(
    state: T,
    satisfiedCondition: UBoolExpr,
    conditionToCheck: UBoolExpr,
    forkToSatisfied: Boolean,
): T? {
    val pathConstraints = state.pathConstraints.clone()
    pathConstraints += conditionToCheck

    if (pathConstraints.isFalse) {
        return null
    }

    val solver = state.ctx.solver<Any?, Type, Any?>()
    val satResult = solver.check(pathConstraints, useSoftConstraints = true)

    return when (satResult) {
        is USatResult -> {
            if (forkToSatisfied) {
                @Suppress("UNCHECKED_CAST")
                val forkedState = state.clone() as T
                // TODO: implement path condition setter (don't forget to reset UMemoryBase:types!)
                state.pathConstraints += conditionToCheck
                state.models = listOf(satResult.model)
                forkedState.pathConstraints += satisfiedCondition
                forkedState
            } else {
                @Suppress("UNCHECKED_CAST")
                val forkedState = state.clone(pathConstraints) as T
                state.pathConstraints += satisfiedCondition
                forkedState.models = listOf(satResult.model)
                forkedState
            }
        }

        is UUnsatResult -> null

        is UUnknownResult -> {
            if (forkToSatisfied) {
                state.pathConstraints += conditionToCheck
            } else {
                state.pathConstraints += satisfiedCondition
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
fun <T : UState<Type, *, *, *>, Type> fork(
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

    val notCondition = state.ctx.mkNot(condition)
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
            condition,
            notCondition,
            forkToSatisfied = false
        )

        falseModels.isNotEmpty() -> {
            val forkedState = forkIfSat(state, notCondition, condition, forkToSatisfied = true)

            if (forkedState != null) {
                state to forkedState
            } else {
                null to state
            }
        }

        else -> error("[trueModels] and [falseModels] are both empty, that has to be impossible by construction!")
    }

    @Suppress("UNCHECKED_CAST")
    return ForkResult(posState, negState as T?)
}
