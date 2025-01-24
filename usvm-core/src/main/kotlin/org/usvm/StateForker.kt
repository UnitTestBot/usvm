package org.usvm

import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

private typealias StateToCheck = Boolean

private const val ForkedState = true
private const val OriginalState = false

sealed interface StateForker {
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
    fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> fork(
        state: T,
        condition: UBoolExpr,
    ): ForkResult<T>

    /**
     * Implements symbolic branching on few disjoint conditions.
     *
     * @return a list of states for each condition - `null` state
     * means [UUnknownResult] or [UUnsatResult] of checking condition.
     */
    fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> forkMulti(
        state: T,
        conditions: Iterable<UBoolExpr>,
    ): List<T?>
}

object WithSolverStateForker : StateForker {
    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> fork(
        state: T,
        condition: UBoolExpr,
    ): ForkResult<T> {
        val (trueModels, falseModels, _) = splitModelsByCondition(state.models, condition)

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

    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> forkMulti(
        state: T,
        conditions: Iterable<UBoolExpr>,
    ): List<T?> {
        var curState = state
        val result = mutableListOf<T?>()
        for (condition in conditions) {
            val (trueModels, _, _) = splitModelsByCondition(curState.models, condition)

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
                    stateToCheck = OriginalState
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

    /**
     * Checks [newConstraintToOriginalState] or [newConstraintToForkedState], depending on the value of [stateToCheck].
     * Depending on the result of checking this condition, do the following:
     * - On [UUnsatResult] - returns `null`;
     * - On [UUnknownResult] - adds [newConstraintToOriginalState] to the path constraints of the [state],
     * and returns null;
     * - On [USatResult] - clones the original state and adds the [newConstraintToForkedState] to it, adds [newConstraintToOriginalState]
     * to the original state, sets the satisfiable model to the corresponding state depending on the [stateToCheck], and returns the
     * forked state.
     *
     */
    @Suppress("MoveVariableDeclarationIntoWhen")
    private fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> forkIfSat(
        state: T,
        newConstraintToOriginalState: UBoolExpr,
        newConstraintToForkedState: UBoolExpr,
        stateToCheck: StateToCheck,
    ): T? {
        val constraintsToCheck = state.pathConstraints.clone()

        constraintsToCheck += if (stateToCheck) {
            newConstraintToForkedState
        } else {
            newConstraintToOriginalState
        }
        val solver = state.ctx.solver<Type>()
        val satResult = solver.check(constraintsToCheck)

        return when (satResult) {
            is UUnsatResult -> {
                // rollback previous ownership
                state.pathConstraints.changeOwnership(state.ownership)
                null
            }

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
                // rollback previous ownership
                state.pathConstraints.changeOwnership(state.ownership)
                state.pathConstraints += if (stateToCheck) newConstraintToOriginalState else newConstraintToForkedState

                null
            }
        }
    }
}

object NoSolverStateForker : StateForker {
    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> fork(
        state: T,
        condition: UBoolExpr,
    ): ForkResult<T> {
        val (trueModels, falseModels, _) = splitModelsByCondition(state.models, condition)
        val notCondition = state.ctx.mkNot(condition)
        val clonedPathConstraints = state.pathConstraints.clone()
        clonedPathConstraints += condition

        val (posState, negState) = if (clonedPathConstraints.isFalse) {
            // changing ownership is unnecessary
            state.pathConstraints += notCondition
            state.models = falseModels

            null to state.takeIf { !it.pathConstraints.isFalse }
        } else {
            val falseState = state.clone()

            // TODO how to reuse "clonedPathConstraints" here?
            state.pathConstraints += condition
            state.models = trueModels

            falseState.pathConstraints += notCondition
            falseState.models = falseModels

            state to falseState.takeIf { !it.pathConstraints.isFalse }
        }

        return ForkResult(posState, negState)
    }

    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> forkMulti(
        state: T,
        conditions: Iterable<UBoolExpr>,
    ): List<T?> {
        var curState = state
        val result = mutableListOf<T?>()
        for (condition in conditions) {
            val (trueModels, _) = splitModelsByCondition(curState.models, condition)
            val clonedConstraints = curState.pathConstraints.clone(MutabilityOwnership(), MutabilityOwnership())
            clonedConstraints += condition

            if (clonedConstraints.isFalse) {
                result += null
                continue
            }

            val nextRoot = curState.clone()

            curState.models = trueModels
            // TODO how to reuse "clonedConstraints"?
            curState.pathConstraints += condition

            result += curState
            curState = nextRoot
        }

        return result
    }
}

/**
 * Splits the passed [models] with this [condition] to the three categories:
 * - models that satisfy this [condition];
 * - models that are in contradiction with this [condition];
 * - models that can not evaluate this [condition].
 */
private fun <Type> splitModelsByCondition(
    models: List<UModelBase<Type>>,
    condition: UBoolExpr,
): SplittedModels<Type> {
    val trueModels = mutableListOf<UModelBase<Type>>()
    val falseModels = mutableListOf<UModelBase<Type>>()
    val unknownModels = mutableListOf<UModelBase<Type>>()

    models.forEach { model ->
        val holdsInModel = model.eval(condition)

        when {
            holdsInModel.isTrue -> trueModels += model
            holdsInModel.isFalse -> falseModels += model
            // Sometimes we cannot evaluate the condition – for example, a result for a division by symbolic expression
            // that is evaluated to 0 is unknown
            else -> unknownModels += model
        }
    }

    return SplittedModels(trueModels, falseModels, unknownModels)
}

data class ForkResult<T>(
    val positiveState: T?,
    val negativeState: T?,
)

private data class SplittedModels<Type>(
    val trueModels: List<UModelBase<Type>>,
    val falseModels: List<UModelBase<Type>>,
    val unknownModels: List<UModelBase<Type>>,
)
