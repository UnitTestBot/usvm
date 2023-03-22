package org.usvm

class UState<Type, Field, Method, Statement>(
    // TODO: add interpreter-specific information
    ctx: UContext,
    typeSystem: UTypeSystem<Type>,
    var callStack: UCallStack<Method, Statement>,
    var path: List<Statement>,
    var pathCondition: UPathCondition = UPathConstraintsSet(ctx),
    var memory: USymbolicMemory<Type> = UMemoryBase<Field, Type, Method>(ctx, typeSystem),
    var models: List<UModel>
) {
    val lastEnteredMethod: Method
        get() = callStack.lastMethod()

    // TODO or last? Do we add a current stmt into the path immediately?
    val currentStatement: Statement?
        get() = path.lastOrNull()

}

@Suppress("UNUSED_PARAMETER")
fun <Type, Field, Method, Statement> fork(state: UState<Type, Field, Method, Statement>, condition: UBoolExpr)
    : Pair<UState<Type, Field, Method, Statement>?, UState<Type, Field, Method, Statement>?>
{
    TODO()
}
