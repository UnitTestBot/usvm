package org.usvm

import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.model.UModelBase
import org.usvm.regions.Region

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
    memory: UMemory<Any, String>, models: List<UModelBase<Any>>,
    pathLocation: PathsTrieNode<TestState, Int>,
) : UState<Any, String, Int, UContext, TestState>(ctx, callStack, pathConstraints, memory, models, pathLocation) {
    override fun clone(newConstraints: UPathConstraints<Any, UContext>?): TestState = this

    override val isExceptional = false
}

interface TestKeyInfo<T, Reg : Region<Reg>> : USymbolicCollectionKeyInfo<T, Reg> {
    override fun mapKey(key: T, composer: UComposer<*>?): T {
        if (composer == null) return key
        return shouldNotBeCalled()
    }

    override fun keyToRegion(key: T): Reg = shouldNotBeCalled()
    override fun eqSymbolic(ctx: UContext, key1: T, key2: T): UBoolExpr = shouldNotBeCalled()
    override fun eqConcrete(key1: T, key2: T): Boolean = shouldNotBeCalled()
    override fun cmpSymbolicLe(ctx: UContext, key1: T, key2: T): UBoolExpr = shouldNotBeCalled()
    override fun cmpConcreteLe(key1: T, key2: T): Boolean = shouldNotBeCalled()
    override fun keyRangeRegion(from: T, to: T): Reg = shouldNotBeCalled()
    override fun topRegion(): Reg = shouldNotBeCalled()
    override fun bottomRegion(): Reg = shouldNotBeCalled()
}
