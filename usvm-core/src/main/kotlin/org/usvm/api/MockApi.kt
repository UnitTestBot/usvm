package org.usvm.api

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState

class MockApi<Type, State : UState<Type, *, *, *, *, State>>(private val state: State) {

    fun <T : USort> makeSymbolicPrimitive(sort: T): UExpr<T> {
//        check(sort != state.ctx.addressSort) { "$sort is not primitive" }
//        return state.ctx.mkFreshConst("symbolic", sort)
        TODO("$sort")
    }

    fun <Type> makeSymbolicRef(type: Type): UHeapRef {
        // todo: make input symbolic refs via state.memory.Mocker
//        return memory.alloc(type)
        TODO("$type")
    }

    fun <Type> makeSymbolicArray(arrayType: Type, size: USizeExpr): UHeapRef {
        // todo: make input symbolic array via state.memory.Mocker
//        return memory.malloc(arrayType, size)
        TODO("$arrayType $size")
    }

    // TODO: add method call mocking

}
