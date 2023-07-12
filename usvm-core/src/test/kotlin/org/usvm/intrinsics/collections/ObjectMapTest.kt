package org.usvm.intrinsics.collections

import io.ksmt.solver.KSolver
import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import org.junit.jupiter.api.RepeatedTest
import org.usvm.*
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.mkSymbolicObjectMap
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapContains
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapGet
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapMergeInto
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapPut
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapRemove
import org.usvm.intrinsics.collections.SymbolicObjectMapIntrinsics.symbolicObjectMapSize
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObjectMapTest : SymbolicCollectionTestBase() {
    @Test
    fun testConcreteMapContains() {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        testMapContains(concreteMap)
    }

    @Test
    fun testSymbolicMapContains() {
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)
        testMapContains(symbolicMap)
    }

    private fun testMapContains(mapRef: UHeapRef) {
        val concreteKeys = (1..5).map { ctx.mkConcreteHeapRef(it) }
        val symbolicKeys = (1..5).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val storedConcrete = concreteKeys.dropLast(1)
        val missedConcrete = concreteKeys.last()
        val storedSymbolic = symbolicKeys.dropLast(1)
        val missedSymbolic = symbolicKeys.last()

        fillMap(mapRef, storedConcrete + storedSymbolic, startValueIdx = 1)

        checkWithSolver {
            (storedConcrete + storedSymbolic).forEach { key ->
                assertImpossible {
                    val keyContains = state.symbolicObjectMapContains(mapRef, key, ctx.sizeSort)
                    keyContains eq falseExpr
                }
            }

            assertImpossible {
                val keyContains = state.symbolicObjectMapContains(mapRef, missedConcrete, ctx.sizeSort)
                keyContains eq trueExpr
            }

            assertPossible {
                val keyContains = state.symbolicObjectMapContains(mapRef, missedSymbolic, ctx.sizeSort)
                keyContains eq falseExpr
            }
        }

        val removeConcrete = storedConcrete.first()
        val removeSymbolic = storedSymbolic.first()
        val removedKeys = listOf(removeConcrete, removeSymbolic)
        removedKeys.forEach { key ->
            state.symbolicObjectMapRemove(mapRef, key, ctx.sizeSort)
        }

        checkWithSolver {
            removedKeys.forEach { key ->
                assertImpossible {
                    val keyContains = state.symbolicObjectMapContains(mapRef, key, ctx.sizeSort)
                    keyContains eq trueExpr
                }
            }
        }
    }

    @Test
    fun testConcreteMapContainsComposition() {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        testMapContainsComposition(concreteMap)
    }

    @Test
    fun testSymbolicMapContainsComposition() {
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)
        testMapContainsComposition(symbolicMap)
    }

    private fun testMapContainsComposition(mapRef: UHeapRef) {
        val concreteKeys = (1..5).map { ctx.mkConcreteHeapRef(it) }
        val symbolicKeys = (1..5).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val otherSymbolicKey = ctx.mkRegisterReading(symbolicKeys.size + 1, ctx.addressSort)

        fillMap(mapRef, concreteKeys + symbolicKeys, startValueIdx = 1)

        val otherKeyContains = state.symbolicObjectMapContains(mapRef, otherSymbolicKey, ctx.sizeSort)
        state.pathConstraints += otherKeyContains
        state.pathConstraints += ctx.mkNot(ctx.mkEq(mapRef, ctx.nullRef))

        val result = uSolver.checkWithSoftConstraints(state.pathConstraints)
        assertIs<USatResult<UModelBase<Field, Type>>>(result)

        assertEquals(ctx.trueExpr, result.model.eval(otherKeyContains))

        val removedKeys = setOf(concreteKeys.first(), symbolicKeys.first(), otherSymbolicKey)
        removedKeys.forEach { key ->
            state.symbolicObjectMapRemove(mapRef, key, ctx.sizeSort)
        }

        val removedKeysValues = removedKeys.mapTo(hashSetOf()) { result.model.eval(it) }
        (concreteKeys + symbolicKeys + otherSymbolicKey).forEach { key ->
            val keyContains = state.symbolicObjectMapContains(mapRef, key, ctx.sizeSort)
            val keyContainsValue = result.model.eval(keyContains)
            val keyValue = result.model.eval(key)

            val expectedResult = ctx.mkBool(keyValue !in removedKeysValues)
            assertEquals(expectedResult, keyContainsValue)
        }
    }

    @Test
    fun testConcreteMapSize() {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        testMapSize(concreteMap) { size, lowerBound, upperBound ->
            assertPossible { size eq upperBound }
            assertPossible { size eq lowerBound }
            assertImpossible {
                mkBvSignedLessExpr(size, lowerBound) or mkBvSignedGreaterExpr(size, upperBound)
            }
        }
    }

    @Test
    fun testSymbolicMapSize() {
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)
        testMapSize(symbolicMap) { size, lowerBound, upperBound ->
            assertPossible { size eq lowerBound }
            assertPossible { size eq upperBound }
        }
    }

    private fun testMapSize(mapRef: UHeapRef, checkSizeBounds: KSolver<*>.(USizeExpr, USizeExpr, USizeExpr) -> Unit) {
        val concreteKeys = (1..5).map { ctx.mkConcreteHeapRef(it) }
        val symbolicKeys = (1..5).map { ctx.mkRegisterReading(it, ctx.addressSort) }

        fillMap(mapRef, concreteKeys + symbolicKeys, startValueIdx = 1)

        checkWithSolver {
            val sizeLowerBound = ctx.mkSizeExpr(concreteKeys.size + 1) // +1 for at least one symbolic key
            val sizeUpperBound = ctx.mkSizeExpr(concreteKeys.size + symbolicKeys.size)

            val actualSize = state.symbolicObjectMapSize(mapRef, ctx.sizeSort)

            checkSizeBounds(actualSize, sizeLowerBound, sizeUpperBound)
        }

        val removedKeys = setOf(concreteKeys.first(), symbolicKeys.first())
        removedKeys.forEach { key ->
            state.symbolicObjectMapRemove(mapRef, key, ctx.sizeSort)
        }

        checkWithSolver {
            /**
             * Size lower bound before remove: concrete size + 1
             * where we add 1 for at least one symbolic key
             *
             * Size after remove is concrete -1 since we remove 1 concrete key and one symbolic.
             * */
            val minKeySize = concreteKeys.size - 1
            val sizeLowerBound = ctx.mkSizeExpr(minKeySize)
            val sizeUpperBound = ctx.mkSizeExpr(concreteKeys.size + symbolicKeys.size - removedKeys.size)

            val actualSize = state.symbolicObjectMapSize(mapRef, ctx.sizeSort)

            checkSizeBounds(actualSize, sizeLowerBound, sizeUpperBound)
        }
    }

    @Test
    fun testMapMergeSymbolicIntoConcrete() = with(state.memory.heap) {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)

        testMapMerge(concreteMap, symbolicMap)
    }

    @Test
    fun testMapMergeConcreteIntoSymbolic() = with(state.memory.heap) {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)

        testMapMerge(concreteMap, symbolicMap)
    }

    @Test
    fun testMapMergeConcreteIntoConcrete() = with(state.memory.heap) {
        val concreteMap0 = state.mkSymbolicObjectMap(ctx.sizeSort)
        val concreteMap1 = state.mkSymbolicObjectMap(ctx.sizeSort)

        testMapMerge(concreteMap0, concreteMap1)
    }

    @Test
    fun testMapMergeSymbolicIntoSymbolic() = with(state.memory.heap) {
        val symbolicMap0 = ctx.mkRegisterReading(99, ctx.addressSort)
        val symbolicMap1 = ctx.mkRegisterReading(999, ctx.addressSort)

        testMapMerge(symbolicMap0, symbolicMap1)
    }

    private fun testMapMerge(mergeTarget: UHeapRef, otherMap: UHeapRef) {
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

        val mergedContains0 = tgtMapKeys.map { state.symbolicObjectMapContains(mergeTarget, it, ctx.sizeSort) }
        val mergedContains1 = otherMapKeys.map { state.symbolicObjectMapContains(mergeTarget, it, ctx.sizeSort) }

        val mergedValues0 = tgtMapKeys.map { state.symbolicObjectMapGet(mergeTarget, it, ctx.sizeSort) }
        val mergedValues1 = otherMapKeys.map { state.symbolicObjectMapGet(mergeTarget, it, ctx.sizeSort) }

        mergedContains0.forEach { checkNoConcreteHeapRefs(it) }
        mergedContains1.forEach { checkNoConcreteHeapRefs(it) }

        mergedValues0.forEach { checkNoConcreteHeapRefs(it) }
        mergedValues1.forEach { checkNoConcreteHeapRefs(it) }

        checkWithSolver {
            val mergedNonOverlapKeys = listOf(
                nonOverlapConcreteKeys0,
                nonOverlapConcreteKeys1,
                nonOverlapSymbolicKeys0,
                nonOverlapSymbolicKeys1
            ).flatten() - removedKeys

            for (key in mergedNonOverlapKeys) {
                val keyContains = state.symbolicObjectMapContains(mergeTarget, key, ctx.sizeSort)
                assertPossible { keyContains eq trueExpr }

                val storedValue = tgtValues[key] ?: otherValues[key] ?: error("$key was not stored")
                val actualValue: USizeExpr = state.symbolicObjectMapGet(mergeTarget, key, ctx.sizeSort).uncheckedCast()
                assertPossible { storedValue eq actualValue }
            }

            for (key in removedKeys) {
                val keyContains = state.symbolicObjectMapContains(mergeTarget, key, ctx.sizeSort)
                assertPossible { keyContains eq falseExpr }
            }

            val overlapKeys = listOf(
                overlapConcreteKeys,
                overlapSymbolicKeys
            ).flatten()

            for (key in overlapKeys) {
                val keyContains = state.symbolicObjectMapContains(mergeTarget, key, ctx.sizeSort)
                assertPossible { keyContains eq trueExpr }

                val storedV1 = tgtValues.getValue(key)
                val storedV2 = otherValues.getValue(key)
                val actualValue: USizeExpr = state.symbolicObjectMapGet(mergeTarget, key, ctx.sizeSort).uncheckedCast()

                assertPossible {
                    (actualValue eq storedV1) or (actualValue eq storedV2)
                }
            }
        }
    }

    @RepeatedTest(10) // Use repeated test since it may randomly fail on some models
    fun testMapMergeSymbolicIntoConcreteComposition() = with(state.memory.heap) {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)

        testMapMergeComposition(concreteMap, symbolicMap)
    }

    @RepeatedTest(10) // Use repeated test since it may randomly fail on some models
    fun testMapMergeConcreteIntoSymbolicComposition() = with(state.memory.heap) {
        val concreteMap = state.mkSymbolicObjectMap(ctx.sizeSort)
        val symbolicMap = ctx.mkRegisterReading(99, ctx.addressSort)

        testMapMergeComposition(concreteMap, symbolicMap)
    }

    @RepeatedTest(10) // Use repeated test since it may randomly fail on some models
    fun testMapMergeConcreteIntoConcreteComposition() = with(state.memory.heap) {
        val concreteMap0 = state.mkSymbolicObjectMap(ctx.sizeSort)
        val concreteMap1 = state.mkSymbolicObjectMap(ctx.sizeSort)

        testMapMergeComposition(concreteMap0, concreteMap1)
    }

    @RepeatedTest(10) // Use repeated test since it may randomly fail on some models
    fun testMapMergeSymbolicIntoSymbolicComposition() = with(state.memory.heap) {
        val symbolicMap0 = ctx.mkRegisterReading(99, ctx.addressSort)
        val symbolicMap1 = ctx.mkRegisterReading(999, ctx.addressSort)

        testMapMergeComposition(symbolicMap0, symbolicMap1)
    }

    private fun testMapMergeComposition(mergeTarget: UHeapRef, otherMap: UHeapRef) {
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

        val otherSymbolicKey = ctx.mkRegisterReading(111, ctx.addressSort)
        val otherKeyContains = state.symbolicObjectMapContains(mergeTarget, otherSymbolicKey, ctx.sizeSort)

        state.pathConstraints += otherKeyContains
        state.pathConstraints += ctx.mkNot(ctx.mkEq(mergeTarget, ctx.nullRef))
        state.pathConstraints += ctx.mkNot(ctx.mkEq(otherMap, ctx.nullRef))

        val result = uSolver.checkWithSoftConstraints(state.pathConstraints)
        assertIs<USatResult<UModelBase<Field, Type>>>(result)

        assertEquals(ctx.trueExpr, result.model.eval(otherKeyContains))

        val allRemovedKeys = removedKeys + setOf(
            nonOverlapConcreteKeys0.last(), nonOverlapConcreteKeys1.last(), otherSymbolicKey
        )
        allRemovedKeys.forEach { key ->
            state.symbolicObjectMapRemove(mergeTarget, key, ctx.sizeSort)
        }

        val removedKeysValues = allRemovedKeys.mapTo(hashSetOf()) { result.model.eval(it) }

        // All stored values are unique
        val storedValuesKeys = (tgtValues.map { it.value to it.key } + otherValues.map { it.value to it.key }).toMap()
        assertEquals(tgtValues.size + otherValues.size, storedValuesKeys.size)

        (tgtMapKeys + otherMapKeys + otherSymbolicKey).forEach { key ->
            val keyContains = state.symbolicObjectMapContains(mergeTarget, key, ctx.sizeSort)
            val keyContainsValue = result.model.eval(keyContains)
            val keyValue = result.model.eval(key)

            val expectedResult = ctx.mkBool(keyValue !in removedKeysValues)
            assertEquals(expectedResult, keyContainsValue)

            if (keyContainsValue.isTrue) {
                val mapValue = state.symbolicObjectMapGet(mergeTarget, key, ctx.sizeSort).asExpr(ctx.sizeSort)
                val actualValue = result.model.eval(mapValue)
                val expectedKey = storedValuesKeys.getValue(actualValue)
                val expectedKeyValue = result.model.eval(expectedKey)
                assertEquals(expectedKeyValue, keyValue)
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
        }.toMap(LinkedHashMap()) // insertion order is important
    }
}
