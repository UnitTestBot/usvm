package org.usvm.intrinsics.collections

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.utils.uncheckedCast
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.mkSymbolicObjectMap
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapContains
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapGet
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapMergeInto
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapPut
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapRemove
import org.usvm.memory.UMemoryBase
import org.usvm.solver.UExprTranslator
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectMapTest {
    private lateinit var ctx: UContext
    private lateinit var pathConstraints: UPathConstraints<Type>
    private lateinit var memory: UMemoryBase<Field, Type, Any?>
    private lateinit var state: StateStub
    private lateinit var translator: UExprTranslator<Field, Type>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = UContext(components)
        pathConstraints = UPathConstraints(ctx)
        memory = UMemoryBase(ctx, pathConstraints.typeConstraints)
        state = StateStub(ctx, pathConstraints, memory)

        translator = UExprTranslator(ctx)
    }

    private class StateStub(
        ctx: UContext,
        pathConstraints: UPathConstraints<Type>,
        memory: UMemoryBase<Field, Type, Any?>
    ) : UState<Type, Field, Any?, Any?>(
        ctx, UCallStack(),
        pathConstraints, memory, emptyList(), persistentListOf()
    ) {
        override fun clone(newConstraints: UPathConstraints<Type>?): UState<Type, Field, Any?, Any?> {
            error("Unsupported")
        }
    }

    @Test
    fun testMapMergeSymbolicIntoConcrete() = with(state.memory.heap) {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)

        runMapMerge(concreteMap, symbolicMap)
    }


    @Test
    fun testMapMergeConcreteIntoSymbolic() = with(state.memory.heap) {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)

        runMapMerge(concreteMap, symbolicMap)
    }

    @Test
    fun testMapMergeConcreteIntoConcrete() = with(state.memory.heap) {
        val concreteMap0 = state.mkSymbolicObjectMap(ctx.sizeSort)
        val concreteMap1 = state.mkSymbolicObjectMap(ctx.sizeSort)

        runMapMerge(concreteMap0, concreteMap1)
    }

    @Test
    fun testMapMergeSymbolicIntoSymbolic() = with(state.memory.heap) {
        val symbolicMap0 = ctx.mkRegisterReading(99, ctx.addressSort)
        val symbolicMap1 = ctx.mkRegisterReading(999, ctx.addressSort)

        runMapMerge(symbolicMap0, symbolicMap1)
    }

    private fun runMapMerge(mergeTarget: UHeapRef, otherMap: UHeapRef) {
        val overlapConcreteKeys = (1..3).map { ctx.mkConcreteHeapRef(it) }
        val nonOverlapConcreteKeys0 = (11..13).map { ctx.mkConcreteHeapRef(it) }
        val nonOverlapConcreteKeys1 = (21..23).map { ctx.mkConcreteHeapRef(it) }

        val overlapSymbolicKeys = (31..33).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val nonOverlapSymbolicKeys0 = (41..43).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val nonOverlapSymbolicKeys1 = (51..53).map { ctx.mkRegisterReading(it, ctx.addressSort) }

        val tgtMapKeys = listOf(
            overlapConcreteKeys,
            nonOverlapConcreteKeys0,
            overlapSymbolicKeys,
            nonOverlapSymbolicKeys0
        ).flatten()

        val otherMapKeys = listOf(
            overlapConcreteKeys,
            nonOverlapConcreteKeys1,
            overlapSymbolicKeys,
            nonOverlapSymbolicKeys1
        ).flatten()

        val removedKeys = setOf(
            nonOverlapConcreteKeys0.first(),
            nonOverlapConcreteKeys1.first()
        )

        val tgtValues = fillMap(mergeTarget, tgtMapKeys, 256)
        val otherValues = fillMap(otherMap, otherMapKeys, 65536)

        for (key in removedKeys) {
            state.symbolicObjectMapRemove(mergeTarget, key, ctx.sizeSort)
            state.symbolicObjectMapRemove(otherMap, key, ctx.sizeSort)
        }

        state.symbolicObjectMapMergeInto(mergeTarget, otherMap, ctx.sizeSort)

        val mergedContains0 = tgtMapKeys.map { state.symbolicObjectMapContains(mergeTarget, it) }
        val mergedContains1 = otherMapKeys.map { state.symbolicObjectMapContains(mergeTarget, it) }

        val mergedValues0 = tgtMapKeys.map { state.symbolicObjectMapGet(mergeTarget, it, ctx.sizeSort) }
        val mergedValues1 = otherMapKeys.map { state.symbolicObjectMapGet(mergeTarget, it, ctx.sizeSort) }

        mergedContains0.forEach { checkNoConcreteHeapRefs(it) }
        mergedContains1.forEach { checkNoConcreteHeapRefs(it) }

        mergedValues0.forEach { checkNoConcreteHeapRefs(it) }
        mergedValues1.forEach { checkNoConcreteHeapRefs(it) }

        KZ3Solver(ctx).use { solver ->
            val mergedNonOverlapKeys = listOf(
                nonOverlapConcreteKeys0,
                nonOverlapConcreteKeys1,
                nonOverlapSymbolicKeys0,
                nonOverlapSymbolicKeys1
            ).flatten() - removedKeys

            for (key in mergedNonOverlapKeys) {
                val keyContains = state.symbolicObjectMapContains(mergeTarget, key)
                solver.assertPossible { keyContains eq trueExpr }

                val storedValue = tgtValues[key] ?: otherValues[key] ?: error("$key was not stored")
                val actualValue: USizeExpr = state.symbolicObjectMapGet(mergeTarget, key, ctx.sizeSort).uncheckedCast()
                solver.assertPossible { storedValue eq actualValue }
            }

            for (key in removedKeys) {
                val keyContains = state.symbolicObjectMapContains(mergeTarget, key)
                solver.assertPossible { keyContains eq falseExpr }
            }

            val overlapKeys = listOf(
                overlapConcreteKeys,
                overlapSymbolicKeys
            ).flatten()

            for (key in overlapKeys) {
                val keyContains = state.symbolicObjectMapContains(mergeTarget, key)
                solver.assertPossible { keyContains eq trueExpr }

                val storedV1 = tgtValues.getValue(key)
                val storedV2 = otherValues.getValue(key)
                val actualValue: USizeExpr = state.symbolicObjectMapGet(mergeTarget, key, ctx.sizeSort).uncheckedCast()

                solver.assertPossible {
                    (actualValue eq storedV1) or (actualValue eq storedV2)
                }
            }
        }
    }

    private fun fillMap(mapRef: UHeapRef, keys: List<UHeapRef>, startValueIdx: Int) = with(state) {
        keys.mapIndexed { index, key ->
            val value = ctx.mkSizeExpr(index + startValueIdx)
            symbolicObjectMapPut(
                mapRef,
                key,
                ctx.sizeSort,
                value
            )
            key to value
        }.toMap()
    }

    private fun checkNoConcreteHeapRefs(expr: UExpr<*>) {
        // Translator throws exception if concrete ref occurs
        translator.translate(expr)
    }

    private fun KSolver<*>.assertPossible(mkCheck: UContext.() -> UBoolExpr) =
        assertStatus(KSolverStatus.SAT) { mkCheck() }

    private fun KSolver<*>.assertImpossible(mkCheck: UContext.() -> UBoolExpr) =
        assertStatus(KSolverStatus.UNSAT) { mkCheck() }

    private fun KSolver<*>.assertStatus(status: KSolverStatus, mkCheck: UContext.() -> UBoolExpr) = try {
        push()

        val expr = ctx.mkCheck()
        val solverExpr = translator.translate(expr)

        assert(solverExpr)

        val actualStatus = check()
        assertEquals(status, actualStatus)
    } finally {
        pop()
    }
}
