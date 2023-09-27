package org.usvm.api

import org.usvm.UContext
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

fun <Type, Method> UState<Type, Method, *, *, *, *>.makeSymbolicRef(type: Type): UHeapRef {
    val ref = memory.mock { call(lastEnteredMethod, emptySequence(), memory.ctx.addressSort) }

    memory.types.addSubtype(ref, type)
    memory.types.addSupertype(ref, type)

    return ref
}

fun <Type, Method, USizeSort : USort, Ctx: UContext<USizeSort>> UState<Type, Method, *, Ctx, *, *>.makeSymbolicArray(
    arrayType: Type,
    size: UExpr<USizeSort>,
): UHeapRef {
    val ref = memory.mock { call(lastEnteredMethod, emptySequence(), memory.ctx.addressSort) }

    memory.types.addSubtype(ref, arrayType)
    memory.types.addSupertype(ref, arrayType)

    memory.writeArrayLength(ref, size, arrayType, pathConstraints.ctx.sizeSort)

    return ref
}

// TODO: add method call mocking
