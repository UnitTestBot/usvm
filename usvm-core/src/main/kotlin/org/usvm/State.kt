package org.usvm

import org.ksmt.sort.KSort
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

abstract class UState<Type, Field, Method, Statement>(
    // TODO: add interpreter-specific information
    val ctx: UContext,
    val typeSystem: UTypeSystem<Type>,
    val callStack: UCallStack<Method, Statement>,
    var pathCondition: UPathCondition,
    val memory: UMemoryBase<Field, Type, Method>,
    var models: PersistentList<UModel>
) {
    constructor(ctx: UContext, typeSystem: UTypeSystem<Type>, callStack: UCallStack<Method, Statement>)
        : this(ctx, typeSystem, callStack, UPathConstraintsSet(), UMemoryBase(ctx, typeSystem), persistentListOf())

    abstract fun clone(): UState<Type, Field, Method, Statement>
}

fun <T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> T.fork(
    condition: UBoolExpr,
    checker: (UMemoryBase<Field, Type, Method>, UPathCondition) -> UModel?,
) : Pair<T?, T?> {
    val (trueModels, falseModels) = models.partition { model ->
        val holdsInModel = model.eval(condition)
        val holds = holdsInModel.isTrue
        check(holds || holdsInModel.isFalse) {
            "Expected true or false, but got $holds"
        }
        holds
    }

    val posPathCondition = pathCondition + condition
    val negPathCondition = pathCondition + ctx.mkNot(condition)

    val (posState, negState) = when {
        trueModels.isNotEmpty() && falseModels.isNotEmpty() -> {
            val posState = this
            val negState = clone()

            posState.pathCondition = posPathCondition
            negState.pathCondition = negPathCondition

            posState.models = trueModels.toPersistentList()
            negState.models = falseModels.toPersistentList()

            posState to negState
        }
        trueModels.isNotEmpty() -> { // falseModels is empty
            val negativeModel = checker(memory, negPathCondition)
            val negState = if (negativeModel != null) {
                val negState = clone()
                negState.pathCondition = negPathCondition
                negState.models = persistentListOf(negativeModel)
                negState
            } else {
                null
            }

            val posState = this
            posState.pathCondition = posPathCondition
            posState.models = trueModels.toPersistentList()
            posState to negState
        }
        falseModels.isNotEmpty() -> { // trueModels is empty
            val positiveModel = checker(memory, posPathCondition)
            val posState = if (positiveModel != null) {
                val posState = clone()
                posState.pathCondition = posPathCondition
                posState.models = persistentListOf(positiveModel)
                posState
            } else {
                null
            }

            val negState = this
            negState.pathCondition = negPathCondition
            negState.models = falseModels.toPersistentList()
            posState to negState
        }
        else -> error("[trueModels] and [falseModels] are both empty, that has to be impossible by construction!")
    }

    @Suppress("UNCHECKED_CAST")
    return posState as T? to negState as T?

}
