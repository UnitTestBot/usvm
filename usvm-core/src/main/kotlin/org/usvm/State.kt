package org.usvm

abstract class UState<Type, Field, Method, Statement>(
    // TODO: add interpreter-specific information
    val ctx: UContext,
    val typeSystem: UTypeSystem<Type>,
    val callStack: UCallStack<Method, Statement>,
    var pathCondition: UPathCondition,
    val memory: UMemoryBase<Field, Type, Method>,
    var models: List<UModel>
) {
    abstract fun clone(): UState<Type, Field, Method, Statement>
}

fun <T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement> T.fork(
    constraint: UBoolExpr,
    checker: (UMemoryBase<Field, Type, Method>, UPathCondition) -> UModel?,
): Pair<T?, T?> {
    val (trueModels, falseModels) = models.partition { model ->
        val holdsInModel = model.eval(constraint)
        val holds = holdsInModel.isTrue
        check(holds || holdsInModel.isFalse) { "Expected true or false, but got $holds" }
        holds
    }

    val posPathCondition = pathCondition + constraint
    val negPathCondition = pathCondition + ctx.mkNot(constraint)

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
            val negativeModel = checker(memory, negPathCondition)
            val negState = if (negativeModel != null) {
                clone().apply { models = listOf(negativeModel) }
            } else {
                null
            }

            val posState = this
            posState to negState
        }

        falseModels.isNotEmpty() -> { // trueModels is empty, so models == falseModels
            val positiveModel = checker(memory, posPathCondition)
            val posState = if (positiveModel != null) {
                clone().apply { models = listOf(positiveModel) }
            } else {
                null
            }

            val negState = this
            posState to negState
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
    return posState as T? to negState as T?

}
