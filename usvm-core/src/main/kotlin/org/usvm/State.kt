package org.usvm

class UState<Type, Field, Method, Statement>(
    // TODO: add interpreter-specific information
    ctx: UContext,
    typeSystem: UTypeSystem<Type>,
    var callStack: UCallStack<Method, Statement>,
    var pathCondition: UPathCondition = UPathConstraintsSet(ctx),
    var memory: USymbolicMemory<Type> = UMemoryBase<Field, Type, Method>(ctx, typeSystem),
    var models: List<UModel>
) {}

@Suppress("UNUSED_PARAMETER")
fun <Type, Field, Method, Statement> fork(state: UState<Type, Field, Method, Statement>, condition: UBoolExpr)
    : Pair<UState<Type, Field, Method, Statement>?, UState<Type, Field, Method, Statement>?>
{
    TODO()
}
