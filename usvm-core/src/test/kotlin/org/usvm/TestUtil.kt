package org.usvm

import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase

typealias Field = java.lang.reflect.Field
typealias Type = kotlin.reflect.KClass<*>
typealias Method = kotlin.reflect.KFunction<*>

fun <T> shouldNotBeCalled(): T {
    error("Should not be called")
}

/**
 * Pseudo random function for tests.
 * Random with constant seed can return different values in different Kotlin versions. This
 * implementation should not have this problem.
 */
internal fun pseudoRandom(i: Int): Int {
    var res = ((i shr 16) xor i) * 0x45d9f3b
    res = ((res shr 16) xor res) * 0x45d9f3b
    res = (res shr 16) xor res
    return res
}

internal class TestState(
    ctx: UContext,
    callStack: UCallStack<String, Int>, pathConstraints: UPathConstraints<Any, UContext>,
    memory: UMemoryBase<Any, Any, String>, models: List<UModelBase<Any, Any>>,
    pathLocation: PathsTrieNode<TestState, Int>,
) : UState<Any, Any, String, Int, UContext, TestState>(ctx, callStack, pathConstraints, memory, models, pathLocation) {
    override fun clone(newConstraints: UPathConstraints<Any, UContext>?): TestState = this

    override val isExceptional = false
}