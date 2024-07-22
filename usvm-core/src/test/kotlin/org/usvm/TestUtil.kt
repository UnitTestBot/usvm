package org.usvm

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.model.UModelBase
import org.usvm.regions.Region
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetsSet

typealias Field = java.lang.reflect.Field
typealias Type = kotlin.reflect.KClass<*>
typealias Method = kotlin.reflect.KFunction<*>
typealias USizeSort = UBv32Sort
typealias TestMethod = String

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

internal class TestTarget(method: TestMethod, offset: Int) : UTarget<TestInstruction, TestTarget>(
    TestInstruction(method, offset)
)

internal class TestState(
    ctx: UContext<*>,
    ownership: MutabilityOwnership,
    callStack: UCallStack<TestMethod, TestInstruction>,
    pathConstraints: UPathConstraints<Any>,
    memory: UMemory<Any, TestMethod>, models: List<UModelBase<Any>>,
    pathLocation: PathNode<TestInstruction>,
    targetTrees: UTargetsSet<TestTarget, TestInstruction> = UTargetsSet.empty(),
    override val entrypoint: TestMethod = ""
) : UState<Any, TestMethod, TestInstruction, UContext<*>, TestTarget, TestState>(ctx, ownership, callStack, pathConstraints, memory, models, pathLocation, PathNode.root(), targetTrees) {

    override fun clone(newConstraints: UPathConstraints<Any>?): TestState = this

    override val isExceptional = false
}

interface TestKeyInfo<T, Reg : Region<Reg>> : USymbolicCollectionKeyInfo<T, Reg> {
    override fun mapKey(key: T, transformer: UTransformer<*, *>?): T {
        if (transformer == null) return key
        return shouldNotBeCalled()
    }

    override fun keyToRegion(key: T): Reg = shouldNotBeCalled()
    override fun eqSymbolic(ctx: UContext<*>, key1: T, key2: T): UBoolExpr = shouldNotBeCalled()
    override fun eqConcrete(key1: T, key2: T): Boolean = shouldNotBeCalled()
    override fun cmpSymbolicLe(ctx: UContext<*>, key1: T, key2: T): UBoolExpr = shouldNotBeCalled()
    override fun cmpConcreteLe(key1: T, key2: T): Boolean = shouldNotBeCalled()
    override fun keyRangeRegion(from: T, to: T): Reg = shouldNotBeCalled()
    override fun topRegion(): Reg = shouldNotBeCalled()
    override fun bottomRegion(): Reg = shouldNotBeCalled()
}

internal fun mockState(id: StateId, startMethod: TestMethod, startInstruction: Int = 0, targets: List<TestTarget> = emptyList()): TestState {
    val ctxMock = mockk<UContext<*>>()
    every { ctxMock.getNextStateId() } returns id
    val callStack = UCallStack<TestMethod, TestInstruction>(startMethod)
    val spyk = spyk(
        TestState(
            ctxMock, MutabilityOwnership(), callStack, mockk(), mockk(), emptyList(), mockk(), UTargetsSet.from(targets)
        )
    )
    every { spyk.currentStatement } returns TestInstruction(startMethod, startInstruction)
    return spyk
}

internal fun callStackOf(startMethod: TestMethod, vararg elements: Pair<TestMethod, Int>): UCallStack<TestMethod, TestInstruction> {
    val callStack = UCallStack<TestMethod, TestInstruction>(startMethod)
    var currentMethod = startMethod
    for ((method, instr) in elements) {
        callStack.push(method, TestInstruction(currentMethod, instr))
        currentMethod = method
    }
    return callStack
}
