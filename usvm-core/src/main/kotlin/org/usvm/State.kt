package org.usvm

import io.ksmt.expr.KInterpretedValue
import kotlinx.collections.immutable.PersistentList
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel
import org.usvm.model.UModelBase
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
private fun <T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> forkIfSat(
    state: T,
    satisfiedCondition: UBoolExpr,
    conditionToCheck: UBoolExpr,
    forkToSatisfied: Boolean
): UState<Type, Field, Method, Statement>? {
    val pathConstraints = state.pathConstraints.clone()
    pathConstraints += conditionToCheck

    if (pathConstraints.isFalse) {
        return null
    }

    val solver = state.ctx.solver<Field, Type, Method>()
    val satResult = solver.check(pathConstraints, useSoftConstraints = true)

    return when (satResult) {
        is USatResult<UModelBase<Field, Type>> -> {
            if (forkToSatisfied) {
                val forkedState = state.clone()
                // TODO: implement path condition setter (don't forget to reset UMemoryBase:types!)
                state.pathConstraints += conditionToCheck
                state.models = listOf(satResult.model)
                forkedState.pathConstraints += satisfiedCondition
                forkedState
            } else {
                val forkedState = state.clone(pathConstraints)
                state.pathConstraints += satisfiedCondition
                forkedState.models = listOf(satResult.model)
                forkedState
            }
        }

        is UUnsatResult<UModelBase<Field, Type>> -> null

        is UUnknownResult<UModelBase<Field, Type>> -> {
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
 * Checks if [condition] and ![condition] are satisfiable within [this] state.
 * If both are satisfiable, copies [this] state and extends path constraints.
 * If only one is satisfiable, returns only [this] state (possibly with [condition] added to path constraints).
 *
 * Important contracts:
 * 1. in return value, at least one of [ForkResult.positiveState] and [ForkResult.negativeState] is not null;
 * 2. makes not more than one query to USolver;
 * 3. if both [condition] and ![condition] are satisfiable, then [ForkResult.positiveState] === [this].
 */
fun <T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> T.fork(condition: UBoolExpr): ForkResult<T> {
    val (trueModels, falseModels) = models.partition { model ->
        val holdsInModel = model.eval(condition)
        check(holdsInModel is KInterpretedValue<UBoolSort>) {
            "Evaluation in model: expected true or false, but got $holdsInModel"
        }
        holdsInModel.isTrue
    }

    val (posState, negState) = when {

        trueModels.isNotEmpty() && falseModels.isNotEmpty() -> {
            val posState = this
            val negState = clone()

            posState.models = trueModels
            negState.models = falseModels
            posState.pathConstraints += condition
            negState.pathConstraints += ctx.mkNot(condition)

            posState to negState
        }

        trueModels.isNotEmpty() -> this to forkIfSat(this, condition, ctx.mkNot(condition), forkToSatisfied = false)

        falseModels.isNotEmpty() -> {
            val forkedState = forkIfSat(this, ctx.mkNot(condition), condition, forkToSatisfied = true)

            if (forkedState != null) {
                this to forkedState
            } else {
                null to this
            }
        }

        else -> error("[trueModels] and [falseModels] are both empty, that has to be impossible by construction!")
    }

    @Suppress("UNCHECKED_CAST")
    return ForkResult(posState, negState as T?)
}
