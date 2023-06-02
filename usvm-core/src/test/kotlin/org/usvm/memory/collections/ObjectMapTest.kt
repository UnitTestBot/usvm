package org.usvm.memory.collections

import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.memory.collections.SymbolicObjectMapIntrinsics.mkSymbolicObjectMap
import org.usvm.memory.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapContains
import org.usvm.memory.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapGet
import org.usvm.memory.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapMergeInto
import org.usvm.memory.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapPut
import org.usvm.solver.UExprTranslator
import kotlin.test.Test

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

        val tgtMapKeys = overlapConcreteKeys + nonOverlapConcreteKeys0 + overlapSymbolicKeys + nonOverlapSymbolicKeys0
        val otherMapKeys = overlapConcreteKeys + nonOverlapConcreteKeys1 + overlapSymbolicKeys + nonOverlapSymbolicKeys1

        fillMap(mergeTarget, tgtMapKeys, 256)
        fillMap(otherMap, otherMapKeys, 65536)

        state.symbolicObjectMapMergeInto(mergeTarget, otherMap, ctx.sizeSort)

        val mergedContains0 = tgtMapKeys.map { state.symbolicObjectMapContains(mergeTarget, it) }
        val mergedContains1 = otherMapKeys.map { state.symbolicObjectMapContains(mergeTarget, it) }

        val mergedValues0 = tgtMapKeys.map { state.symbolicObjectMapGet(mergeTarget, it, ctx.sizeSort) }
        val mergedValues1 = otherMapKeys.map { state.symbolicObjectMapGet(mergeTarget, it, ctx.sizeSort) }

        mergedContains0.forEach { checkNoConcreteHeapRefs(it) }
        mergedContains1.forEach { checkNoConcreteHeapRefs(it) }

        mergedValues0.forEach { checkNoConcreteHeapRefs(it) }
        mergedValues1.forEach { checkNoConcreteHeapRefs(it) }
    }

    private fun fillMap(mapRef: UHeapRef, keys: List<UHeapRef>, startValueIdx: Int) = with(state) {
        keys.forEachIndexed { index, key ->
            symbolicObjectMapPut(
                mapRef,
                key,
                ctx.sizeSort,
                ctx.mkSizeExpr(index + startValueIdx)
            )
        }
    }

    private fun checkNoConcreteHeapRefs(expr: UExpr<*>) {
        // Translator throws exception if concrete ref occurs
        translator.translate(expr)
    }
}
