package org.usvm.intrinsics

import org.usvm.*

object EngineIntrinsics {
    fun UState<*, *, *, *>.assume(expr: UBoolExpr) {
        pathConstraints += expr
    }

    fun <T : USort> UState<*, *, *, *>.makeSymbolicPrimitive(sort: T): UExpr<T> {
        check(sort != ctx.addressSort) { "$sort is not primitive" }
        return ctx.mkFreshConst("symbolic", sort)
    }

    fun <Type> UState<Type, *, *, *>.makeSymbolicRef(type: Type): UHeapRef {
        // todo: make input symbolic refs
        return memory.alloc(type)
    }

    fun <Type> UState<Type, *, *, *>.makeSymbolicArray(arrayType: Type, size: USizeExpr): UHeapRef {
        // todo: make input symbolic array
        return memory.malloc(arrayType, size)
    }

    fun UState<*, *, *, *>.objectTypeEquals(lhs: UHeapRef, rhs: UHeapRef): UBoolExpr {
        TODO("Objects types equality check: $lhs, $rhs")
    }
}
