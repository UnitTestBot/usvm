package org.usvm.api

import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.uctx
import org.usvm.utils.logAssertFailure

// TODO: special mock api for variables

fun <Method, T : USort> UState<*, Method, *, *, *, *>.makeSymbolicPrimitive(
    sort: T
): UExpr<T> {
    check(sort != sort.uctx.addressSort) { "$sort is not primitive" }
    return memory.mocker.createMockSymbol(trackedLiteral = null, sort, ownership)
}

fun <Type, Method, State> StepScope<State, Type, *, *>.makeSymbolicRef(
    type: Type
): UHeapRef? where State : UState<Type, Method, *, *, *, State> =
    mockSymbolicRef { memory.types.evalTypeEquals(it, type) }

fun <Type, Method, State> StepScope<State, Type, *, *>.makeSymbolicRefWithSameType(
    representative: UHeapRef
): UHeapRef? where State : UState<Type, Method, *, *, *, State> =
    mockSymbolicRef { objectTypeEquals(it, representative) }

fun <Type, Method, State> StepScope<State, Type, *, *>.makeNullableSymbolicRefWithSameType(
    representative: UHeapRef
): UHeapRef? where State : UState<Type, Method, *, *, *, State> =
    mockSymbolicRef { ctx.mkOr(objectTypeEquals(it, representative), ctx.mkEq(it, ctx.nullRef)) }

fun <Method> UState<*, Method, *, *, *, *>.makeSymbolicRefUntyped(): UHeapRef =
    memory.mocker.createMockSymbol(trackedLiteral = null, ctx.addressSort, ownership)

private inline fun <Type, Method, State> StepScope<State, Type, *, *>.mockSymbolicRef(
    crossinline mkTypeConstraint: State.(UHeapRef) -> UBoolExpr
): UHeapRef? where State : UState<Type, Method, *, *, *, State> {
    val ref = calcOnState { makeSymbolicRefUntyped() }

    val typeConstraint = calcOnState {
        mkTypeConstraint(ref)
    }

    assert(typeConstraint)
        .logAssertFailure { "Constraint violation: Type constraint in mockSymbolicRef" }
        ?: return null

    return ref
}

// TODO: add method call mocking
