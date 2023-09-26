package org.usvm

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.model.UModelBase
import org.usvm.regions.Region
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetController

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

internal class TestTargetController : UTargetController {
    override val targets: MutableCollection<UTarget<*, *, *>>
        get() = error("Must not be called")
}

internal class TestTarget(method: String, offset: Int) : UTarget<TestInstruction, TestTarget, TestTargetController>(
    TestInstruction(method, offset)
) {
    fun reach(state: TestState) {
        propagate(state)
    }
}

internal class TestState(
    ctx: UContext,
    callStack: UCallStack<String, TestInstruction>, pathConstraints: UPathConstraints<Any>,
    memory: UMemory<Any, String>, models: List<UModelBase<Any>>,
    pathLocation: PathsTrieNode<TestState, TestInstruction>,
    targetTrees: List<TestTarget> = emptyList()
) : UState<Any, String, TestInstruction, UContext, TestTarget, TestState>(ctx, callStack, pathConstraints, memory, models, pathLocation, targetTrees) {

    override fun clone(newConstraints: UPathConstraints<Any>?): TestState = this

    override val isExceptional = false
}

interface TestKeyInfo<T, Reg : Region<Reg>> : USymbolicCollectionKeyInfo<T, Reg> {
    override fun mapKey(key: T, transformer: UTransformer<*>?): T {
        if (transformer == null) return key
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

internal fun mockState(id: StateId, startMethod: String, startInstruction: Int = 0, targets: List<TestTarget> = emptyList()): TestState {
    val ctxMock = mockk<UContext>()
    every { ctxMock.getNextStateId() } returns id
    val callStack = UCallStack<String, TestInstruction>(startMethod)
    val spyk = spyk(TestState(ctxMock, callStack, mockk(), mockk(), emptyList(), mockk(), targets))
    every { spyk.currentStatement } returns TestInstruction(startMethod, startInstruction)
    return spyk
}

internal fun callStackOf(startMethod: String, vararg elements: Pair<String, Int>): UCallStack<String, TestInstruction> {
    val callStack = UCallStack<String, TestInstruction>(startMethod)
    var currentMethod = startMethod
    for ((method, instr) in elements) {
        callStack.push(method, TestInstruction(currentMethod, instr))
        currentMethod = method
    }
    return callStack
}
