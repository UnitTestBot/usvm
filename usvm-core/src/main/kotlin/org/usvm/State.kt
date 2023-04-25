package org.usvm

import kotlinx.collections.immutable.PersistentList
import org.ksmt.expr.KInterpretedValue
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

abstract class UState<Type, Field, Method, Statement>(
    // TODO: add interpreter-specific information
    val ctx: UContext,
    val typeSystem: UTypeSystem<Type>,
    val callStack: UCallStack<Method, Statement>,
    var pathCondition: UPathCondition,
    val memory: UMemoryBase<Field, Type, Method>,
    var models: List<UModel>,
    var path: PersistentList<Statement>,
) {
    abstract fun clone(): UState<Type, Field, Method, Statement>
}

class ForkResult<T>(
    val positiveState: T?,
    val negativeState: T?,
) {
    operator fun component1(): T? = positiveState
    operator fun component2(): T? = negativeState
}

private fun <T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> cloneStateWithModelOrNull(
    state: T,
    model: UModel?,
): UState<Type, Field, Method, Statement>? = model?.let { state.clone().apply { models = listOf(model) } }

fun <T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> T.fork(
    condition: UBoolExpr,
    findModel: (UMemoryBase<Field, Type, Method>, UPathCondition) -> UModel?,
): ForkResult<T> {
    val (trueModels, falseModels) = models.partition { model ->
        val holdsInModel = model.eval(condition)
        check(holdsInModel is KInterpretedValue<UBoolSort>) { "Expected true or false, but got $holdsInModel" }
        holdsInModel.isTrue
    }

    val posPathCondition = pathCondition + condition
    val negPathCondition = pathCondition + ctx.mkNot(condition)

    val (posState, negState) = when {
        posPathCondition == pathCondition -> this to null

        negPathCondition == pathCondition -> null to this

        trueModels.isNotEmpty() && falseModels.isNotEmpty() -> {
            val posState = this
            val negState = clone()

            posState.models = trueModels
            negState.models = falseModels

            posState to negState
        }

        trueModels.isNotEmpty() -> { // falseModels is empty, so models == trueModels
            val negativeModel = findModel(memory, negPathCondition)
            val negState = cloneStateWithModelOrNull(this, negativeModel)

            this to negState
        }

        falseModels.isNotEmpty() -> { // trueModels is empty, so models == falseModels
            val positiveModel = findModel(memory, posPathCondition)
            val posState = cloneStateWithModelOrNull(this, positiveModel)

            posState to this
        }

        else -> error("[trueModels] and [falseModels] are both empty, that has to be impossible by construction!")
    }

    if (posState != null) {
        posState.pathCondition = posPathCondition
    }
    if (negState != null) {
        negState.pathCondition = negPathCondition
    }

    @Suppress("UNCHECKED_CAST")
    return ForkResult(posState as T?, negState as T?)

}
