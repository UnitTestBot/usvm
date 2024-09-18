package org.usvm.state

import io.ksmt.utils.cast
import org.usvm.ForkResult
import org.usvm.StateForker
import org.usvm.StateForker.Companion.splitModelsByCondition
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UJoinedBoolExpr
import org.usvm.UState
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.unwrapJoinedExpr

private typealias StateToCheck = Boolean

private const val ForkedState = true
private const val OriginalState = false

object TSStateForker : StateForker {
    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> fork(
        state: T,
        condition: UBoolExpr,
    ): ForkResult<T> {
        val unwrappedCondition: UBoolExpr = condition.unwrapJoinedExpr(state.ctx).cast()
        val (trueModels, falseModels, _) = splitModelsByCondition(state.models, unwrappedCondition)

        val notCondition = if (condition is UJoinedBoolExpr) condition.not() else state.ctx.mkNot(unwrappedCondition)
        val (posState, negState) = when {

            trueModels.isNotEmpty() && falseModels.isNotEmpty() -> {
                val posState = state
                val negState = state.clone()

                posState.models = trueModels
                negState.models = falseModels
                posState.pathConstraints += unwrappedCondition
                negState.pathConstraints += notCondition

                posState to negState
            }

            trueModels.isNotEmpty() -> state to forkIfSat(
                state,
                newConstraintToOriginalState = unwrappedCondition,
                newConstraintToForkedState = notCondition,
                stateToCheck = ForkedState
            )

            falseModels.isNotEmpty() -> {
                val forkedState = forkIfSat(
                    state,
                    newConstraintToOriginalState = unwrappedCondition,
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
                state.pathConstraints += if (stateToCheck) newConstraintToOriginalState else newConstraintToForkedState

                null
            }
        }
    }
}
