package org.usvm.api

import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.uctx

// TODO: special mock api for variables

fun <Method, T : USort> UState<*, Method, *, *, *, *>.makeSymbolicPrimitive(
    sort: T
): UExpr<T> {
    check(sort != sort.uctx.addressSort) { "$sort is not primitive" }
    return memory.mock { call(lastEnteredMethod, emptySequence(), sort) }
}

fun <Type, Method, State> StepScope<State, Type, *, *>.makeSymbolicRef(
    type: Type
): UHeapRef? where State : UState<Type, Method, *, *, *, State> =
    mockSymbolicRef { memory.types.evalTypeEquals(it, type) }

fun <Type, Method, State> StepScope<State, Type, *, *>.makeSymbolicRefWithSameType(
    representative: UHeapRef
): UHeapRef? where State : UState<Type, Method, *, *, *, State> =
    mockSymbolicRef { objectTypeEquals(it, representative) }

private inline fun <Type, Method, State> StepScope<State, Type, *, *>.mockSymbolicRef(
    crossinline mkTypeConstraint: State.(UHeapRef) -> UBoolExpr
): UHeapRef? where State : UState<Type, Method, *, *, *, State> {
    val ref = calcOnState {
        memory.mock { call(lastEnteredMethod, emptySequence(), memory.ctx.addressSort) }
    }

    val typeConstraint = calcOnState {
        mkTypeConstraint(ref)
    }

    assert(typeConstraint) ?: return null

    return ref
}

// TODO: add method call mocking
